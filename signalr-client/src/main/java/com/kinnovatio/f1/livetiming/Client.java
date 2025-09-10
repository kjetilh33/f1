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
import io.prometheus.metrics.core.metrics.StateSet;
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

    // connector components
    //private static ConnectorStatusHttpServer statusHttpServer;
    private static F1HubConnection hubConnection;
    private static State connectorState = State.UNKNOWN;
    private static Instant lastSessionCheck = Instant.now();
    private static Instant lastMessageReceived = Instant.now();
    private static final String defaultSessionStringValue = "No information available";
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static SessionInfo sessionInfo = null;
    private static final StatsMonitor statsMonitor = StatsMonitor.create().start();

    // Metrics configs. From config file / env variables
    private static final boolean enableMetrics =
            ConfigProvider.getConfig().getValue("metrics.enable", Boolean.class);

    /*
    Metrics section. Define the metrics to expose.
     */
    static final Counter recordReceivedCounter = Counter.builder()
            .name("record_received_total")
            .help("Total number of live timing records received")
            .register();

    static final Counter messageReceivedCounter = Counter.builder()
            .name("message_received_total")
            .help("Total number of live timing messages received")
            .labelNames("category")
            .register();

    static final Counter messageSentCounter = Counter.builder()
            .name("message_sent_total")
            .help("Total number of messages sent to Kafka")
            .labelNames("category")
            .register();


    static final StateSet connectorSessionState = StateSet.builder()
            .name("connector.session_state")
            .help("Connector session state")
            .states("sessionActive")
            .register();

    static final Gauge errorGauge= Gauge.builder()
            .name("job.errors").help("Total job errors")
            .register();

    /*
    The entry point of the code. It executes the main logic and push job metrics upon completion.
     */
    public static void main(String[] args) {
        try {
            // Execute the main logic
            run();

        } catch (Exception e) {
            LOG.error("Unrecoverable error. Will exit. {}", e.toString());
            errorGauge.inc();
        }
    }

    /*
    The main logic to execute.
     */
    private static void run() throws Exception {
        LOG.info("Starting container...");

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
        executorService.scheduleAtFixedRate(Client::asyncKeepAliveLoop, 10, 2, TimeUnit.SECONDS);
    }

    private static void useSignalrCustomClient() throws Exception {
        hubConnection = F1HubConnection.of(signalRBaseUrl) //.of(signalRBaseUrl) of(testBaseUrl)
                //.enableMessageLogging(true)
                .withConsumer(Client::processMessage)
                ;
        hubConnection.connect();
    }

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

    private static void processLiveTimingMessage(LiveTimingMessage message) {
        messageReceivedCounter.labelValues(message.category()).inc();
        lastMessageReceived = Instant.now();
        statsMonitor.addToMessageQueue(message);
        statsMonitor.incMessageCounter();

        if (message.category().equalsIgnoreCase("SessionInfo")
                || message.category().equalsIgnoreCase("SessionData")) {
            updateSessionStatus(message);
        }
    }

    private static void updateSessionStatus(LiveTimingMessage message) {
        String loggingPrefix = "updateSessionStatus() - ";

        if (message.category().equalsIgnoreCase("SessionInfo")) {
            LOG.info(message.toString());
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
            LOG.info(message.toString());
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
            connectorState = State.UNKNOWN;
        } else if (sessionInfo.status().equalsIgnoreCase("Started")
                || sessionInfo.archiveStatus().equalsIgnoreCase("Generating")) {
            connectorState = State.LIVE_SESSION;
        } else {
            connectorState = State.NO_SESSION;
        }

        // Set timestamp of last evaluation
        lastSessionCheck = Instant.now();
    }

    public static Optional<SessionInfo> getSessionInfo() {
        return Optional.ofNullable(sessionInfo);
    }

    public static F1HubConnection getHubConnection() {
        return hubConnection;
    }

    public static ConnectorStatus getConnectorStatus() {
        return new ConnectorStatus(connectorState.getStatus(), lastSessionCheck, statsMonitor.getMessagesFromQueue(),
                statsMonitor.getMessageRatePerSecond(), statsMonitor.getMessageRatePerMinute());
    }

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
            } else if (Duration.between(lastSessionCheck, Instant.now()).compareTo(Duration.ofMinutes(20)) > 0) {
                // It has been 20 mins since we last checked if there is a session starting
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

    enum State {
        NO_SESSION ("Waiting for next session to start."),
        LIVE_SESSION ("Streaming live timing data."),
        UNKNOWN ("Unknown state.");

        private final String status;

        public String getStatus() {
            return status;
        }

        State(String status) {
            this.status = status;
        }
    }
}