package com.kinnovatio.signalr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.kinnovatio.signalr.messages.LiveTimingHubResponseMessage;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import com.kinnovatio.signalr.messages.MessageDecoder;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/// Represents a connection to the Formula 1 SignalR live timing hub.
/// This class handles the negotiation, connection, and communication with the F1 SignalR service
/// over WebSockets. It manages the connection lifecycle, including keep-alive messages and
/// automatic reconnection.
///
/// Use the static factory methods [#create()] to instantiate.
/// Once created, configure it using methods like [#withConsumer(Consumer)] and then
/// call [#connect()] to establish the connection.
public final class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final Path defaultPathMessageLog = Path.of("./received-messages.log");

    // Constants for the F1 SignalR service
    private static final String wssConnect = "wss://livetiming.formula1.com/signalrcore";
    private static final String negotiateUrl = "https://livetiming.formula1.com/signalrcore/negotiate";

    /// The data streams to subscribe to for receiving all live timing data.
    private static final String[] dataStreams = {"Heartbeat",
            "ExtrapolatedClock", "TopThree", "RcmSeries",
            "TimingStats", "TimingAppData", "TeamRadio",
            "WeatherData", "TrackStatus", "DriverList",
            "RaceControlMessages", "SessionInfo", "SessionStatus",
            "SessionData", "LapCount", "TimingData",
            "PitLaneTimeCollection",
            "CarData.z", "Position.z", "ChampionshipPrediction",
            "PitStopSeries", "PitStop"
    };

    // Internal state management
    private OperationalState operationalState = OperationalState.CLOSED;

    // Signalr connection management
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private HubConnection hubConnection = null;

    private final Consumer<LiveTimingRecord> consumer;
    private final boolean messageLogEnabled;

    // Metrics fields
    static final Counter recordReceivedCounter = Counter.builder()
            .name("livetiming_connector_websocket_record_received_total")
            .help("Total number of live timing records received")
            .labelNames("category")
            .register();

    static final Counter invalidRecordReceivedCounter = Counter.builder()
            .name("livetiming_connector_websocket_record_received_invalid_total")
            .help("Total number of live timing records received")
            .register();

    static final Gauge connectorOperationalState = Gauge.builder()
            .name("livetiming_connector_operational_state")
            .help("Connector operational state")
            .register();

    private F1HubConnection(Consumer<LiveTimingRecord> consumer, boolean messageLogEnabled) {
        this.consumer = consumer;
        this.messageLogEnabled = messageLogEnabled;
    }

    /// Creates a new F1HubConnection with default settings.
    ///
    /// @return a new instance of [F1HubConnection].
    public static F1HubConnection create() {
        return new F1HubConnection(null, false);
    }

    public Consumer<LiveTimingRecord> getConsumer() {
        return consumer;
    }

    public boolean isMessageLogEnabled() {
        return messageLogEnabled;
    }

    /// Enables or disables logging of all received raw messages to a file.
    ///
    /// @param enable `true` to enable logging, `false` to disable.
    /// @return a new instance with the updated setting.
    public F1HubConnection enableMessageLogging(boolean enable) {
        return new F1HubConnection(this.consumer, enable);
    }

    /// Sets the consumer that will receive [LiveTimingRecord]s.
    ///
    /// @param consumer The consumer to process incoming messages.
    /// @return a new instance with the updated consumer.
    public F1HubConnection withConsumer(Consumer<LiveTimingRecord> consumer) {
        return new F1HubConnection(consumer, this.messageLogEnabled);
    }

    /// Initiate a SignalR connection. This method will try to set up a connection over websocket.
    ///
    /// @return `true` if the connection was set up successfully. `false` otherwise.
    public boolean connect() {
        connect(false);
        return true;
    }

    /// Checks if the client is currently connected to the SignalR hub.
    ///
    /// @return `true` if the connection state is CONNECTED. `false` otherwise.
    public boolean isConnected() {
        if (hubConnection == null) return false;
        return hubConnection.getConnectionState() == HubConnectionState.CONNECTED;
    }

    /// Gets the high-level operational state of the client.
    /// This indicates whether the client is actively trying to maintain a connection
    /// (`OPEN`) or has been shut down (`CLOSED`).
    ///
    /// @return The current operational state as a string (e.g., "OPEN", "CLOSED").
    public String getOperationalState() {
        return operationalState.toString();
    }

    /// Sets the high-level operational state of the client and updates the corresponding metric.
    ///
    /// @param operationalState The new operational state.
    private void setOperationalState(OperationalState operationalState) {
        LOG.info("F1HubConnection - changing operational state from {} to {}", this.operationalState, operationalState);
        this.operationalState = operationalState;
        connectorOperationalState.set(operationalState.getStatusValue());
    }

    private synchronized boolean connect(boolean forceConnect) {
        String loggingPrefix = "connect() - ";

        if (operationalState == OperationalState.OPEN && !forceConnect) {
            LOG.warn(loggingPrefix + "The connection is already open. Connect() has no effect.");
            return true;
        }

        connectSignalR(forceConnect);
        return true;
    }

    /// Gracefully closes the connection to the F1 SignalR hub and cleans up resources.
    ///
    /// This method signals the client to shut down by setting the operational state to `CLOSED`,
    /// which prevents the background keep-alive task from attempting any new reconnections.
    public synchronized void close() {
        if (null != hubConnection) {
            hubConnection.stop().blockingAwait();
            hubConnection = null;
        }
        setOperationalState(OperationalState.CLOSED);
    }

    private void connectSignalR(boolean forceConnect) {
        // Fix for potential leak: close any existing connection before opening a new one
        if (hubConnection != null && forceConnect) {
            try {
                LOG.info("Closing existing connection before initializing new one.");
                hubConnection.stop().blockingAwait();
            } catch (Exception e) {
                LOG.warn("Failed to stop previous hub connection: {}", e.toString());
            }
        }

        // Get the necessary cookie headers
        String cookie = getCookie(negotiateUrl).orElse("");

        hubConnection = HubConnectionBuilder.create(wssConnect)
                .withHeader("Cookie", cookie)
                .build();

        hubConnection.onClosed(exception -> {
            if (exception != null) {
                LOG.warn("The hub closed the connection: {}", exception.getMessage());
            } else {
                LOG.info("Closed the connection to the hub.");
            }
            setOperationalState(F1HubConnection.OperationalState.CLOSED);
        });

        // Register the main handler for the livetiming feed
        hubConnection.<JsonElement, JsonElement, JsonElement>on("feed",
                this::onFeed,
                JsonElement.class, JsonElement.class, JsonElement.class);

        hubConnection.start()
                .blockingAwait();
        LOG.info("Connected to SignalR hub with connection id {}", hubConnection.getConnectionId());

        Single<JsonElement> response = hubConnection.invoke(JsonElement.class, "Subscribe", List.of(dataStreams));
        response.subscribeWith(new DisposableSingleObserver<JsonElement>() {
            @Override
            public void onStart() {
                LOG.info("Calling hub to start subscribing to messages...");
            }

            @Override
            public void onSuccess(JsonElement value) {
                LOG.info("Successfully started subscribing to messages from the hub.");
                onHubResponse(value);
            }

            @Override
            public void onError(Throwable error) {
                LOG.error("Error while subscribing to messages from the hub: " + error.toString());
                close();
            }
        });

        setOperationalState(OperationalState.OPEN);
    }

    /// Obtain an Amazon load balancer cookie.
    /// Need this to make subsequent calls to the SignalR hub.
    private Optional<String> getCookie(String negotiateUrl) {
        String loggingPrefix = "getCookie() - ";

        try {
            // 1. Perform the OPTIONS request
            HttpRequest optionsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(negotiateUrl))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(optionsRequest, HttpResponse.BodyHandlers.discarding());
            LOG.debug(loggingPrefix + "Negotiate response:\n {}", response.toString());
            LOG.debug(loggingPrefix + "Response headers: \n{}", response.headers().toString());
            LOG.debug(loggingPrefix + "Response body: \n{}", response);

            // 2. Extract the AWSALBCORS cookie from the 'Set-Cookie' header
            List<String> setCookie = response.headers().allValues("Set-Cookie");
            LOG.debug(loggingPrefix + "Parsed set-cookie header: \n{}", setCookie.toString());

            for (String cookie : setCookie) {
                if (cookie.contains("AWSALBCORS")) {
                    LOG.debug(loggingPrefix + "Found AWSALBCORS cookie: \n{}", cookie.split(";")[0]);
                    return Optional.of(cookie.split(";")[0]);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            LOG.warn(loggingPrefix + "Error getting cookie: {}", e.toString());
            return Optional.empty();
        }
    }

    private void onHubResponse(JsonElement response) {
        // Store the messages on disk if logging is enabled
        if (isMessageLogEnabled()) {
            logMessage(response.toString());
        }

        Optional<LiveTimingHubResponseMessage> liveTimingHubResponseMessage = MessageDecoder.parseHubResponseMessage(response);
        if (liveTimingHubResponseMessage.isPresent()) {
            liveTimingHubResponseMessage.get().messages().forEach(message -> {
                recordReceivedCounter.labelValues(message.category()).inc();
                notifySubscribers(message);
            });
        } else {
            invalidRecordReceivedCounter.inc();
        }
    }

    private void onFeed(JsonElement category, JsonElement message, JsonElement timeStamp) {
        LOG.debug("onFeed() - Received {} characters feed messages.", message.toString().length());
        LOG.debug("onFeed() -  1: {}...", category.toString().substring(0, Math.min(200, category.toString().length())));
        LOG.debug("onFeed() -  2: {}...", message.toString().substring(0, Math.min(200, message.toString().length())));
        LOG.debug("onFeed() -  3: {}...", timeStamp.toString().substring(0, Math.min(200, timeStamp.toString().length())));


        // Store the messages on disk if logging is enabled
        if (isMessageLogEnabled()) {
            JsonArray jsonArray = new JsonArray();
            jsonArray.add(category);
            jsonArray.add(message);
            jsonArray.add(timeStamp);
            logMessage(jsonArray.toString());
        }

        Optional<LiveTimingMessage> liveTimingMessage = MessageDecoder.parseMessageFeed(category, message, timeStamp);
        if (liveTimingMessage.isPresent()) {
            recordReceivedCounter.labelValues(liveTimingMessage.get().category()).inc();
            notifySubscribers(liveTimingMessage.get());
        } else {
            invalidRecordReceivedCounter.inc();
        }
    }

    private void logMessage(String message) {
        try {
            Files.writeString(defaultPathMessageLog, message + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Failed to write message to log file: {}", e.toString());
        }
    }

    private void notifySubscribers(LiveTimingRecord record) {
        if (null != getConsumer()) {
            getConsumer().accept(record);
        }
    }

    /// Defines the high-level operational state of the F1HubConnection client.
    enum OperationalState {
        CLOSED(0),
        OPEN(1);

        private final int statusValue;

        public int getStatusValue() {
            return statusValue;
        }

        OperationalState(int statusValue) {
            this.statusValue = statusValue;
        }
    }
}
