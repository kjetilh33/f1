package com.kinnovatio.signalr;

import com.google.gson.JsonElement;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import tools.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import com.kinnovatio.signalr.messages.MessageDecoder;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.smallrye.common.constraint.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;


/// Represents a connection to the Formula 1 SignalR live timing hub.
/// This class handles the negotiation, connection, and communication with the F1 SignalR service
/// over WebSockets. It manages the connection lifecycle, including keep-alive messages and
/// automatic reconnection.
///
/// Use the static factory methods [#create()] or [#of(String)] to instantiate.
/// Once created, configure it using methods like [#withConsumer(Consumer)] and then
/// call [#connect()] to establish the connection. After connecting, call
/// This class is designed to be immutable through the use of AutoValue. Configuration methods
/// return a new instance with the updated configuration.
@AutoValue
public abstract class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path defaultPathMessageLog = Path.of("./received-messages.log");

    // Constants for the F1 SignalR service
    private static final String baseUrl = "https://livetiming.formula1.com/signalr/";
    private static final String connectionData = """
                [{"name": "streaming"}]
                """;

    /// The data streams to subscribe to for receiving all live timing data.
    private static final String[] dataStreams = {"Heartbeat",
            "ExtrapolatedClock", "TopThree", "RcmSeries",
            "TimingStats", "TimingAppData", "TeamRadio",
            "WeatherData", "TrackStatus", "DriverList",
            "RaceControlMessages", "SessionInfo", "SessionStatus",
            "SessionData", "LapCount", "TimingData",
            "PitLaneTimeCollection",
            // subscription only?
            "CarData.z", "Position.z", "ChampionshipPrediction",
            // Not sure if these work now?
            "PitStopSeries", "PitStop"
    };

    // Internal state management
    private OperationalState operationalState = OperationalState.CLOSED;
    private ScheduledExecutorService executorService = null;

    // Signalr connection management
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private HubConnection hubConnection;

    // Metrics fields
    static final Counter recordReceivedCounter = Counter.builder()
            .name("livetiming_connector_websocket_record_received_total")
            .help("Total number of live timing records received")
            .labelNames("category")
            .register();

    static final Gauge connectorOperationalState = Gauge.builder()
            .name("livetiming_connector_operational_state")
            .help("Connector operational state")
            .register();

    private static F1HubConnection.Builder builder() {
        return new AutoValue_F1HubConnection.Builder()
                .setMessageLogEnabled(false);
    }

    /// Creates a new F1HubConnection with the default base URL.
    ///
    /// @return a new instance of [F1HubConnection].
    /// @throws RuntimeException if the default base URL is invalid.
    public static F1HubConnection create() {
        try {
            return F1HubConnection.of(baseUrl);
        } catch (URISyntaxException e) {
            LOG.error("Unable to create connection to the default base URL: {}", e.toString());
            throw new RuntimeException(e);
        }
    }

    /// Creates a new F1HubConnection with a specified base URI.
    ///
    /// @param baseUri The base URI string for the SignalR service.
    /// @return a new instance of [F1HubConnection].
    /// @throws URISyntaxException if the provided baseUri string is not a valid URI.
    public static F1HubConnection of(String baseUri) throws URISyntaxException {
        return F1HubConnection.of(new URI(baseUri));
    }

    /// Creates a new F1HubConnection with a specified base URI.
    ///
    /// @param baseUri The base URI for the SignalR service.
    /// @return a new instance of [F1HubConnection].
    public static F1HubConnection of(URI baseUri) {
        return F1HubConnection.builder()
                .setBaseUri(baseUri)
                .build();
    }

    protected abstract Builder toBuilder();

    public abstract URI getBaseUri();

    @Nullable
    public abstract Consumer<LiveTimingRecord> getConsumer();
    public abstract boolean isMessageLogEnabled();

    /// Enables or disables logging of all received raw messages to a file.
    ///
    /// @param enable `true` to enable logging, `false` to disable.
    /// @return a new instance with the updated setting.
    public F1HubConnection enableMessageLogging(boolean enable) {
        return toBuilder().setMessageLogEnabled(enable).build();
    }

    /// Sets the consumer that will receive [LiveTimingRecord]s.
    ///
    /// @param consumer The consumer to process incoming messages.
    /// @return a new instance with the updated consumer.
    public F1HubConnection withConsumer(Consumer<LiveTimingRecord> consumer) {
        return toBuilder().setConsumer(consumer).build();
    }

    /// Initiate a SignalR connection. This method will try to set up a connection over websocket. Once the
    /// connection is ready, you have to call
    /// messages.
    ///
    /// @return `true` if the connection was set up successfully. `false` otherwise.
    public boolean connect() {
        connect(false);

        return true;
    }

    /// Checks if the client is currently connected to the SignalR hub.
    ///
    /// @return `true` if the connection state is `false` otherwise.
    public boolean isConnected() {
        return operationalState == OperationalState.OPEN;
    }

    /// Gets the high-level operational state of the client.
    /// This indicates whether the client is actively trying to maintain a connection
    /// (`OPEN`) or has been shut down (`CLOSED`).
    ///
    /// @return The current operational state as a string (e.g., "OPEN", "CLOSED").
    /// @see OperationalState
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


    private boolean connect(boolean forceConnect) {
        String loggingPrefix = "connect() - ";

        if (operationalState == OperationalState.OPEN  && !forceConnect) {
            LOG.warn(loggingPrefix + "The connection is already open. Connect() has no effect.");
            return true;
        }

        connectSignalR(forceConnect);

        return true;
    }


    /// Gracefully closes the connection to the F1 SignalR hub and cleans up resources.
    ///
    /// This method signals the client to shut down by setting the operational state to `CLOSED`,
    /// which prevents the background keep-alive task from attempting any new reconnections. It then
    /// initiates an orderly shutdown of the scheduled executor service that manages the connection.
    public void close() {
        if (null != executorService) executorService.shutdown();
        if (null != hubConnection) hubConnection.stop();

        setOperationalState(OperationalState.CLOSED);
    }

    private void connectSignalR(boolean forceConnect) {
        String wssConnect = "wss://livetiming.formula1.com/signalrcore";
        String negotiateUrl = "https://livetiming.formula1.com/signalrcore/negotiate";

        // Get the necessary cookie headers
        String cookie = getCookie(negotiateUrl).orElse("");

        hubConnection = HubConnectionBuilder.create(wssConnect)
                .withHeader("Cookie", cookie)
                .build();

        hubConnection.onClosed(exception -> {
            LOG.warn("The hub closed the connection: {}", exception.getMessage());
            setOperationalState(F1HubConnection.OperationalState.CLOSED);
        });

        // Register the main handler for the livetiming feed
        /*
        hubConnection.<JsonElement>on("feed",
                (Action1<JsonElement>) (userList) -> onFeed(userList),
                JsonElement.class);

         */

        hubConnection.<JsonElement, JsonElement, JsonElement>on("feed",
                (element1, element2, element3) -> onFeed(element1, element2, element3),
                JsonElement.class, JsonElement.class, JsonElement.class);

        hubConnection.start()
                .blockingAwait();
        LOG.info("Connected to SignalR hub with connection id {}", hubConnection.getConnectionId());

        //Observable<String> subscription = hubConnection.stream(String.class, "Subscribe", List.of(List.of(dataStreams)));
        //subscription.subscribe(message -> LOG.info("Received signalR message: {}", message));
        //hubConnection.send("Subscribe", List.of(dataStreams));

        Single<JsonElement> response =  hubConnection.invoke(JsonElement.class, "Subscribe", List.of(dataStreams));
        response.subscribeWith(new DisposableSingleObserver<JsonElement>() {
            @Override
            public void onStart() {
                LOG.info("Calling hub to start subscribing to messages...");
            }

            @Override
            public void onSuccess(JsonElement value) {
                LOG.info("Hub invoke success. Response: " + value.toString());
            }

            @Override
            public void onError(Throwable error) {
                LOG.error(error.toString());
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

    private void onFeed(JsonElement element) {
        LOG.info("onFeed() - Received {} characters feed messages.", element.getAsString().length());
        LOG.info("onFeed() - Message: {}...", element.getAsString().substring(0, Math.min(200, element.getAsString().length())));

        // Store the messages on disk if logging is enabled
        if (isMessageLogEnabled()) {
            logMessage(element.toString());
        }

        //recordReceivedCounter.labelValues(recordCategory).inc();

        notifySubscribers(element);

        //args.forEach(message -> LOG.info("Message: {}", message));
    }

    private void onFeed(JsonElement element1, JsonElement element2, JsonElement element3) {
        LOG.info("onFeed() - Received {} characters feed messages.", element1.getAsString().length());
        LOG.info("onFeed() -  1: {}...", element1.toString().substring(0, Math.min(200, element1.toString().length())));
        LOG.info("onFeed() -  2: {}...", element2.toString().substring(0, Math.min(200, element2.toString().length())));
        LOG.info("onFeed() -  3: {}...", element3.toString().substring(0, Math.min(200, element3.toString().length())));


        // Store the messages on disk if logging is enabled
        if (isMessageLogEnabled()) {
            logMessage(element1.toString());
        }

        //recordReceivedCounter.labelValues(recordCategory).inc();

        //notifySubscribers(element);

        //args.forEach(message -> LOG.info("Message: {}", message));
    }

    private void logMessage(String message) {
        try {
            Files.writeString(defaultPathMessageLog, message + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Failed to write message to log file: {}", e.toString());
        }
    }

    /// Parses a raw message string from the SignalR hub and notifies the registered consumer.
    ///
    /// This method takes the raw JSON payload from the WebSocket, which can contain an array of
    /// different data updates (e.g., TimingData, TimingAppData), and uses the [MessageDecoder]
    /// to parse it into a list of [LiveTimingMessage] objects.
    ///
    /// If a consumer has been registered via [#withConsumer(Consumer)], this method iterates
    /// through the parsed messages and passes each one to the consumer's `accept` method for processing.
    ///
    /// @param element The raw JSON string received from the WebSocket.
    private void notifySubscribers(JsonElement element) {
        String loggingPrefix = "notifySubscribers() - ";
        LOG.debug(loggingPrefix + "Raw Json message: {}", element);

        // Need to make sure we have a registered consumer for the messages
        if (null != getConsumer()) {
            List<? extends LiveTimingRecord> messages = MessageDecoder.parseLiveTimingMessages(element);
            LOG.debug(loggingPrefix + "Parsed raw message into {} live timing messages", messages.size());
            messages.forEach(message -> getConsumer().accept(message));
        }
    }

    /// Defines the high-level operational state of the F1HubConnection client.
    /// This state determines whether the client is actively trying to maintain a connection
    /// or if it has been shut down. It is distinct from the [State] enum, which
    /// tracks the more granular status of the underlying WebSocket connection.
    enum OperationalState {
        /// The client is shut down. In this state, no new connections will be attempted,
        /// and the background keep-alive and reconnection tasks will not run. This is the
        /// initial state and the state after [#close()] is called.
        CLOSED(0),
        /// The client is active. It will attempt to establish and maintain a connection
        /// to the SignalR hub. The background keep-alive and reconnection logic is active
        /// in this state. This state is set by a call to [#connect()].
        OPEN(1);

        private final int statusValue;

        public int getStatusValue() {
            return statusValue;
        }

        OperationalState(int statusValue) {
            this.statusValue = statusValue;
        }
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setMessageLogEnabled(boolean value);
        abstract Builder setBaseUri(URI value);
        abstract Builder setConsumer(Consumer<LiveTimingRecord> value);

        abstract F1HubConnection build();
    }
}
