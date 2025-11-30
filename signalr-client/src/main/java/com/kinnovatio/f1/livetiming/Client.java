package com.kinnovatio.f1.livetiming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.F1HubConnection;
import com.kinnovatio.signalr.messages.LiveTimingHubResponseMessage;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/// The main entry point for the F1 Live Timing SignalR client application.
///
/// This class is responsible for initializing and orchestrating all the major components:
///
///   - Connecting to the F1 SignalR hub using [F1HubConnection].
///   - Processing incoming live timing messages and forwarding them to Kafka if enabled.
///   - Managing the application's lifecycle and connection state based on F1 session activity.
///   - Exposing application status and metrics via HTTP endpoints.
///
public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /*
    Configuration section. The configuration values are read from the following locations (in order of precedence):
    1. Environment variables
    2. Configuration file at /config/config.yaml
    3. The default config file at ./resources/META-INF/microprofile-config.yaml (packaged with the code)

    A configuration variable "foo.bar" resolves to the following input:
    - Environment variable named "foo_bar" (dot "." is replaced by underscore "_")
    - Config file yaml entry: "
    metrics:
        bar: "the-value"
    "
     */
    // Connection variables
    private static final String signalRBaseUrl =
            ConfigProvider.getConfig().getValue("source.baseUrl", String.class);

    /// Flag to control whether messages are published to Kafka.
    /// Loaded from the "target.kafka.enable" configuration property.
    private static final boolean enableKafka =
            ConfigProvider.getConfig().getValue("target.kafka.enable", Boolean.class);

    // connector components
    //private static ConnectorStatusHttpServer statusHttpServer;
    /// The connection to the F1 SignalR hub.
    private static F1HubConnection hubConnection;
    /// The high-level operational state of this client application.
    private static State connectorState = State.UNKNOWN;
    /// Timestamp of the last time the F1 session status was checked.
    private static Instant lastSessionCheck = Instant.now();
    /// Timestamp of the last message received from the F1 hub.
    private static Instant lastMessageReceived = Instant.now();
    private static final String defaultSessionStringValue = "No information available";
    /// Executor service for the background keep-alive and state management loop.
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    /// Holds the latest information about the current or upcoming F1 session.
    private static SessionInfo sessionInfo = null;
    /// A monitor for tracking message statistics like rate and queue size.
    private static final StatsMonitor statsMonitor = StatsMonitor.create().start();

    // Metrics configs. From config file / env variables
    /// Flag to control whether the Prometheus metrics server is started.
    /// Loaded from the "metrics.enable" configuration property.
    private static final boolean enableMetrics =
            ConfigProvider.getConfig().getValue("metrics.enable", Boolean.class);

    /*
    Metrics section. Define the metrics to expose.
     */
    /// A Prometheus counter for the total number of [LiveTimingRecord]s received
    /// from the WebSocket connection.
    static final Counter recordReceivedCounter = Counter.builder()
            .name("livetiming_connector_record_received_total")
            .help("Total number of live timing records received")
            .register();

    /// A Prometheus counter for the total number of individual [LiveTimingMessage]s
    /// processed, labeled by their category (e.g., "TimingData", "SessionInfo").
    static final Counter messageReceivedCounter = Counter.builder()
            .name("livetiming_connector_message_received_total")
            .help("Total number of live timing messages received")
            .labelNames("category")
            .register();

    /// A Prometheus gauge representing the current [State] of the connector,
    /// indicating whether it's live, waiting, or in an unknown state.
    static final Gauge connectorSessionStateGauge = Gauge.builder()
            .name("livetiming_connector_session_state")
            .help("Connector session state")
            .register();

    /// A Prometheus gauge to track the total number of unrecoverable errors.
    static final Gauge errorGauge= Gauge.builder()
            .name("job.errors").help("Total job errors")
            .register();


    /// The main entry point for the application.
    /// It initializes and runs the client, catching any unrecoverable exceptions.
    ///
    /// @param args Command line arguments (not used).
    public static void main(String[] args) {
        try {
            // Execute the main logic
            run();

        } catch (Exception e) {
            LOG.error("Unrecoverable error. Will exit. {}", e.toString());
            errorGauge.inc();
        }
    }

    /// Initializes and starts all application components.
    /// This includes the SignalR connection, the status HTTP server, the metrics server,
    /// and the background task for connection management.
    /// @throws Exception if initialization of the SignalR client fails.
    private static void run() throws Exception {
        LOG.info("Starting container...");

        // Set the initial connector state to UNKNOWN
        connectorSessionStateGauge.set(0);

        // Start the F1 hub connection
        useSignalrCustomClient();
        // Start the connector status http server
        ConnectorStatusHttpServer.create().start();

        // Start the metrics http server
        if (enableMetrics) {
            JvmMetrics.builder().register(); // initialize the out-of-the-box JVM metrics
            io.prometheus.metrics.exporter.httpserver.HTTPServer metricsHttpServer =
                    io.prometheus.metrics.exporter.httpserver.HTTPServer.builder()
                    .port(9400)
                    .buildAndStart();
            LOG.info("HTTP server for metrics started on port 9400");
        }

        // Start the background keep-alive task
        executorService.scheduleAtFixedRate(Client::asyncKeepAliveLoop, 20, 5, TimeUnit.SECONDS);
    }

    /// Creates and connects the [F1HubConnection] instance.
    /// It configures the connection with the base URL from properties and sets
    /// [#processMessage(LiveTimingRecord)] as the consumer for incoming data.
    /// @throws Exception if the connection cannot be established.
    private static void useSignalrCustomClient() throws Exception {
        hubConnection = F1HubConnection.of(signalRBaseUrl) //.of(signalRBaseUrl) of(testBaseUrl)
                //.enableMessageLogging(true)
                .withConsumer(Client::processMessage)
                ;
        hubConnection.connect();
    }

    /// The primary callback method for processing all data received from the [F1HubConnection].
    /// It increments the record counter and delegates the contained messages to the appropriate handler.
    ///
    /// @param message The [LiveTimingRecord] received from the hub, which can be a single message
    ///                or a container for multiple messages.
    private static void processMessage(LiveTimingRecord message) {
        LOG.debug("Received live timing record: {}", message);
        recordReceivedCounter.inc();

        switch (message) {
            case LiveTimingHubResponseMessage hubResponse -> {
                List<LiveTimingMessage> messages = hubResponse.messages();
                messages.forEach(Client::processLiveTimingMessage);
            }
            case LiveTimingMessage timingMessage -> {
                processLiveTimingMessage(timingMessage);
            }
        }
    }

    /// Processes an individual [LiveTimingMessage].
    /// This method updates metrics, forwards the message to Kafka (if enabled),
    /// and passes session-related messages to [#updateSessionStatus(LiveTimingMessage)].
    ///
    /// @param message The live timing message to process.
    private static void processLiveTimingMessage(LiveTimingMessage message) {
        messageReceivedCounter.labelValues(message.category()).inc();
        lastMessageReceived = Instant.now();
        statsMonitor.addToMessageQueue(message);
        statsMonitor.incMessageCounter();

        if (enableKafka) {
            KafkaProducer.getInstance().publish(message);
        }

        if (message.category().equalsIgnoreCase("SessionInfo")
                || message.category().equalsIgnoreCase("SessionData")) {
            updateSessionStatus(message);
        }
    }

    /// Parses `SessionInfo` and `SessionData` messages to update the application's
    /// understanding of the current F1 session state.
    /// It extracts details like session status ("Started", "Finished") and updates the global [#sessionInfo] object.
    ///
    /// @param message A [LiveTimingMessage] with the category "SessionInfo" or "SessionData".
    private static void updateSessionStatus(LiveTimingMessage message) {
        String loggingPrefix = "updateSessionStatus() - ";

        if (message.category().equalsIgnoreCase("SessionInfo")) {
            LOG.info(loggingPrefix + message.toString());
            try {
                Objects.requireNonNull(message.message());
                JsonNode root = objectMapper.readTree(message.message());

                // set default/fallback values
                String meetingName = defaultSessionStringValue;
                String sessionStatus = defaultSessionStringValue;
                String archiveStatus = defaultSessionStringValue;
                String sessionType = defaultSessionStringValue;
                String startDateString = defaultSessionStringValue;
                String endDateString = defaultSessionStringValue;

                if (sessionInfo != null) {
                    // We have existing session info. Use it.
                    meetingName = sessionInfo.meetingName();
                    sessionStatus = sessionInfo.status();
                    archiveStatus = sessionInfo.archiveStatus();
                    sessionType = sessionInfo.type();
                    startDateString = sessionInfo.startDate();
                    endDateString = sessionInfo.endDate();
                }

                // Add the newest update from the message
                meetingName = root.path("Meeting").path("Name").asText(meetingName);
                sessionStatus = root.path("SessionStatus").asText(sessionStatus);
                archiveStatus = root.path("ArchiveStatus").path("Status").asText(archiveStatus);
                sessionType = root.path("Type").asText(sessionType);
                startDateString = root.path("StartDate").asText(startDateString);
                endDateString = root.path("EndDate").asText(endDateString);

                LOG.info(loggingPrefix + "We have an updated session status: {}", sessionStatus);

                // Store the session info
                sessionInfo = new SessionInfo(meetingName, sessionStatus, sessionType, startDateString, endDateString, archiveStatus);
            } catch (JsonProcessingException e) {
                LOG.warn(loggingPrefix + "Failed to process session info message. Error: {}", e);
            }
        } else if (message.category().equalsIgnoreCase("SessionData")) {
            LOG.info(loggingPrefix + message.toString());
            try {
                Objects.requireNonNull(message.message());
                JsonNode root = objectMapper.readTree(message.message());

                // set default/fallback values
                String meetingName = defaultSessionStringValue;
                String sessionStatus = defaultSessionStringValue;
                String archiveStatus = defaultSessionStringValue;
                String sessionType = defaultSessionStringValue;
                String startDateString = defaultSessionStringValue;
                String endDateString = defaultSessionStringValue;

                if (sessionInfo != null) {
                    // We have existing session info. Use it.
                    meetingName = sessionInfo.meetingName();
                    sessionStatus = sessionInfo.status();
                    archiveStatus = sessionInfo.archiveStatus();
                    sessionType = sessionInfo.type();
                    startDateString = sessionInfo.startDate();
                    endDateString = sessionInfo.endDate();
                }

                // Add the newest update from the message
                if (root.path("StatusSeries").isObject()) {
                    // we may have status as a single object entry.
                    for (JsonNode entry : root.path("StatusSeries")) {
                        if (entry.path("SessionStatus").isTextual()) {
                            sessionStatus = entry.path("SessionStatus").asText(sessionStatus);
                            LOG.info(loggingPrefix + "We have an updated session status: {}", sessionStatus);
                        }
                    }
                } else if (root.path("StatusSeries").isArray()) {
                    // We have a collection of status objects. Need to iterate over them.
                    for (JsonNode entry : root.path("StatusSeries")) {
                        if (entry.path("SessionStatus").isTextual()) {
                            sessionStatus = entry.path("SessionStatus").asText(sessionStatus);
                            LOG.info(loggingPrefix + "We have an updated session status: {}", sessionStatus);
                        }
                    }
                }

                // Store the session info
                sessionInfo = new SessionInfo(meetingName, sessionStatus, sessionType, startDateString, endDateString, archiveStatus);
            } catch (JsonProcessingException e) {
                LOG.warn("Failed to process session data message. Error: {}", e);
            }
        }

        // Evaluate if we have an active session running or not
        if (sessionInfo == null) {
            setConnectorState(State.UNKNOWN);
        } else if (sessionInfo.status().equalsIgnoreCase("Started")
                || sessionInfo.archiveStatus().equalsIgnoreCase("Generating")) {
            setConnectorState(State.LIVE_SESSION);
        } else {
            setConnectorState(State.NO_SESSION);
        }

        // Set timestamp of last evaluation
        lastSessionCheck = Instant.now();
    }

    /// Gets the current F1 session information.
    ///
    /// @return An [Optional] containing the [SessionInfo] if available, otherwise an empty Optional.
    public static Optional<SessionInfo> getSessionInfo() {
        return Optional.ofNullable(sessionInfo);
    }

    /// Gets the active [F1HubConnection] instance.
    ///
    /// @return The F1 hub connection client.
    public static F1HubConnection getHubConnection() {
        return hubConnection;
    }

    /// Gets a snapshot of the connector's current status.
    ///
    /// @return A [ConnectorStatus] object containing the current state, last check time, and message statistics.
    public static ConnectorStatus getConnectorStatus() {
        return new ConnectorStatus(connectorState.getStatus(), lastSessionCheck, statsMonitor.getMessagesFromQueue(),
                statsMonitor.getMessageRatePerSecond(), statsMonitor.getMessageRatePerMinute());
    }

    /// A background task that periodically checks the health of the connection and the state of the F1 session.
    /// It implements the core logic for automatically connecting and disconnecting from the SignalR hub
    /// based on whether an F1 session is active, starting soon, or has finished. This helps to conserve
    /// resources by not maintaining a connection when no data is expected.
    private static void asyncKeepAliveLoop() {
        final String loggingPrefix = "Connector connection loop - ";
        Duration timeSinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        LOG.debug(loggingPrefix + "Connector state = {}", connectorState);
        LOG.debug(loggingPrefix + "Session info = {}", sessionInfo);
        LOG.debug(loggingPrefix + "F1 connection hub connection state = {}", hubConnection.getConnectionState());
        LOG.debug(loggingPrefix + "F1 connection hub operational state = {}", hubConnection.getOperationalState());

        if (connectorState == State.UNKNOWN && Duration.between(lastSessionCheck, Instant.now()).compareTo(Duration.ofSeconds(60)) < 0) {
            // Let's wait for 60 seconds for the connector to connect and evaluate the session state.
            return;
        }

        if (connectorState == State.LIVE_SESSION || timeSinceLastMessage.compareTo(Duration.ofSeconds(30)) < 0) {
            // We have an ongoing session (or we have messages flowing through)
            if (hubConnection.isConnected()) {
                // All is good. We have a race session and the connector is running. Do nothing.
            } else {
                LOG.info(loggingPrefix + "Looks like we don't have a connection to the F1 hub. Will try to reconnect...");
                try {
                    hubConnection.connect();
                } catch (Exception e) {
                    LOG.warn("Error connecting to hub: {}", e.toString());
                }
            }
        } else if (connectorState == State.NO_SESSION) {
            if (hubConnection.isConnected()) {
                // The session is over, so we can disconnect from the F1 live hub.
                LOG.info(loggingPrefix + "There is no race session currently, but our live timing connection is open. Will close it...");
                hubConnection.close();
            } else if (Duration.between(lastSessionCheck, Instant.now()).compareTo(Duration.ofMinutes(10)) > 0) {
                // It has been 10 mins since we last checked if there is a session starting
                LOG.info(loggingPrefix + "Checking to see if a session will start soon. Will try to reconnect...");
                try {
                    hubConnection.connect();
                } catch (Exception e) {
                    LOG.warn("Error connecting to hub: {}", e.toString());
                }
            }
        } else {
            // We are in "unknown", and have been in over 60 sec. Let's disconnect and reconnect
            LOG.info(loggingPrefix + "Not able to determine if we have a race session ongoing or now. Will restart the connection.");
            hubConnection.close();
            try {
                hubConnection.connect();
            } catch (Exception e) {
                LOG.warn("Error connecting to hub: {}", e.toString());
            }
        }
    }

    /// Sets the high-level operational state of the client.
    ///
    /// @param connectorState The new state to set.
    private static void setConnectorState(State connectorState) {
        LOG.info("Client - changing connector state from {} to {}", Client.connectorState, connectorState);
        Client.connectorState = connectorState;
        connectorSessionStateGauge.set(connectorState.getStatusValue());
    }

    /// Represents the high-level operational state of the client application,
    /// primarily concerning its relationship to an F1 session.
    enum State {
        UNKNOWN (0, "Unknown state."),
        NO_SESSION (1,"Waiting for next session to start."),
        LIVE_SESSION (2,"Streaming live timing data.");

        private final int statusValue;
        private final String status;

        public int getStatusValue() {
            return statusValue;
        }

        public String getStatus() {
            return status;
        }

        State(int statusValue,String status) {
            this.statusValue = statusValue;
            this.status = status;
        }
    }
}