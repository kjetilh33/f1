package com.kinnovatio.signalr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.auto.value.AutoValue;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import com.kinnovatio.signalr.messages.MessageDecoder;
import com.kinnovatio.signalr.messages.transport.*;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.smallrye.common.constraint.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// Represents a connection to the Formula 1 SignalR live timing hub.
/// This class handles the negotiation, connection, and communication with the F1 SignalR service
/// over WebSockets. It manages the connection lifecycle, including keep-alive messages and
/// automatic reconnection.
///
/// Use the static factory methods [#create()] or [#of(String)] to instantiate.
/// Once created, configure it using methods like [#withConsumer(Consumer)] and then
/// call [#connect()] to establish the connection. After connecting, call
/// [#subscribeToAll()] to start receiving data.
/// This class is designed to be immutable through the use of AutoValue. Configuration methods
/// return a new instance with the updated configuration.
@AutoValue
public abstract class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path defaultPathMessageLog = Path.of("./received-messages.log");

    // Constants for the F1 SignalR service
    private static final String baseUrl = "https://livetiming.formula1.com/signalr/";
    private static final String negotiatePath = "negotiate";
    private static final String clientProtocolKey = "clientProtocol";
    private static final String clientProtocol = "1.5";
    private static final String connectionDataKey = "connectionData";
    private static final String connectionData = """
                [{"name": "streaming"}]
                """;

    /// The data streams to subscribe to for receiving all live timing data.
    private static final String[] dataStreams = {"Heartbeat",
            "ExtrapolatedClock", "TopThree", "RcmSeries",
            "TimingStats", "TimingAppData", "TeamRadio",
            "WeatherData", "TrackStatus", "DriverList",
            "RaceControlMessages", "SessionInfo",
            "SessionData", "LapCount", "TimingData",
            "PitLaneTimeCollection",
            // subscription only?
            "CarData.z", "Position.z", "ChampionshipPrediction",
            // Not sure if these work now?
            "PitStopSeries", "PitStop"
    };

    // Internal state management
    private State connectionState = State.READY;
    private OperationalState operationalState = OperationalState.CLOSED;
    private ScheduledExecutorService executorService = null;

    // Keep-alive and connection management fields
    private Instant lastKeepAliveMessage = Instant.now();
    private Duration keepAliveTimeout = Duration.ofSeconds(30);
    private HttpClient httpClient = null;
    private WebSocket webSocket = null;
    private final SignalrWssListener wssListener = new SignalrWssListener();
    private int errorCounter = 0;

    // Metrics fields
    static final Counter recordReceivedCounter = Counter.builder()
            .name("livetiming_connector_websocket_record_received_total")
            .help("Total number of live timing records received")
            .labelNames("category")
            .register();

    static final Gauge connectorConnectionState = Gauge.builder()
            .name("livetiming_connector_connection_state")
            .help("Connector connection state")
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
    /// connection is ready, you have to call [#subscribeToAll()] to start receiving live timing
    /// messages.
    ///
    /// @return `true` if the connection was set up successfully. `false` otherwise.
    /// @throws IOException if something goes wrong at the network layer.
    /// @throws InterruptedException if the working thread gets interrupted.
    public boolean connect() throws IOException, InterruptedException {
        return connect(false);
    }

    /// Checks if the client is currently connected to the SignalR hub.
    ///
    /// @return `true` if the connection state is [#OPEN], `false` otherwise.
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
    
    /// Gets the low-level connection state of the underlying WebSocket.
    /// This provides a granular status of the connection process, such as whether it is
    /// connecting, connected, or disconnected.
    ///
    /// @return The current connection state as a string (e.g., "READY", "CONNECTING", "CONNECTED").
    /// @see State
    public String getConnectionState() {
        return connectionState.toString();
    }

    /// Sets the high-level operational state of the client and updates the corresponding metric.
    ///
    /// @param operationalState The new operational state.
    private void setOperationalState(OperationalState operationalState) {
        LOG.info("F1HubConnection - changing operational state from {} to {}", this.operationalState, operationalState);
        this.operationalState = operationalState;
        connectorOperationalState.set(operationalState.getStatusValue());
    }

    /// Sets the low-level connection state of the WebSocket and updates the corresponding metric.
    ///
    /// @param connectionState The new connection state.
    private void setConnectionState(State connectionState) {
        LOG.info("F1HubConnection - changing SignalR connection state from {} to {}", this.connectionState, connectionState);
        this.connectionState = connectionState;
        connectorConnectionState.set(connectionState.getStatusValue());
    }

    /// Initiates or forces a reconnection to the SignalR hub.
    ///
    /// This is the internal implementation of the connection logic. It manages the entire
    /// lifecycle of establishing a connection, including:
    ///
    ///   - Closing any existing WebSocket connection.
    ///   - Performing the SignalR negotiation to obtain a connection token.
    ///   - Establishing a new WebSocket connection.
    ///   - Starting a background task ([#asyncKeepAliveLoop()]) to handle keep-alives
    ///     and automatic reconnections.
    ///
    /// The `forceConnect` parameter allows this method to be used for both the initial
    /// user-triggered connection and for internal reconnections when a connection is lost.
    ///
    /// @param forceConnect If `true`, forces a new connection even if the operational state
    ///                     is already `OPEN`. If `false`, the method will return
    ///                     without action if the connection is already considered open.
    /// @return `true` if the connection was successfully initiated, `false` if the
    ///         connection was already open and `forceConnect` was `false`.
    /// @throws IOException if an I/O error occurs during negotiation or if the connection times out.
    /// @throws InterruptedException if the thread is interrupted during the connection process.
    private boolean connect(boolean forceConnect) throws IOException, InterruptedException {
        String loggingPrefix = "connect() - ";

        if (operationalState == OperationalState.OPEN  && !forceConnect) {
            LOG.warn(loggingPrefix + "The connection is already open. Connect() has no effect.");
            return true;
        }

        // In case we have an open websocket, close it
        if (null != webSocket) {
            LOG.warn(loggingPrefix + "The websocket connection is already open. Will close it before reconnecting.");
            this.close();
        }

        // Check if we need to instantiate the http client
        if (null == httpClient) httpClient = HttpClient.newHttpClient();

        try {
            Instant startInstant = Instant.now();
            lastKeepAliveMessage = Instant.now();
            setConnectionState(State.READY);
            setOperationalState(OperationalState.OPEN);

            webSocket = negotiateWebsocket();

            // try for 20 seconds to establish a SignalR connection
            while (Duration.between(startInstant, Instant.now()).compareTo(Duration.ofSeconds(20)) < 0
                    && connectionState != State.CONNECTED) {
                LOG.debug("Checking connection state. State: {}, duration: {}", connectionState, Duration.between(startInstant, Instant.now()));
                Thread.sleep(1000);
            }
            if (connectionState != State.CONNECTED) {
                throw new IOException("Timeout. Unable to establish connection to hub.");
            }

            // Check the executor service
            if (null == executorService || executorService.isShutdown()) executorService = Executors.newSingleThreadScheduledExecutor();

            // Start a scheduled task to check state (close, reconnect)
            executorService.scheduleAtFixedRate(this::asyncKeepAliveLoop, 5, 1, TimeUnit.SECONDS);

            // Start subscribing to the F1 live timing data.
            webSocket.sendText(MessageDecoder.toMessageJson(
                "Streaming",                        // hub
                    "Subscribe",                        // method
                    List.of(List.of(dataStreams)),      // arguments
                    1),                                 // identifier
                    true);
        } catch (Exception e) {
            LOG.warn(loggingPrefix + "Failed to negotiate a connection. Will try to clean up resources. Error: {}", e.toString());
            close();
            throw e;
        }

        return true;
    }


    /// Gracefully closes the connection to the F1 SignalR hub and cleans up resources.
    ///
    /// This method signals the client to shut down by setting the operational state to `CLOSED`,
    /// which prevents the background keep-alive task from attempting any new reconnections. It then
    /// initiates an orderly shutdown of the scheduled executor service that manages the connection.
    public void close() {
        setOperationalState(OperationalState.CLOSED);

        if (null != executorService) executorService.shutdown();
        if (null != webSocket) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
            webSocket.abort();
            webSocket = null;
        }
        if (connectionState != State.READY) setConnectionState(State.READY);
    }

    /// A periodic task that runs in the background to monitor and maintain the hub connection.
    /// This method is designed to be executed by a [ScheduledExecutorService].
    ///
    /// Its responsibilities include:
    ///
    ///   - **Reconnecting:** If the connection state is not `CONNECTED` or `CONNECTING`,
    ///     it attempts to re-establish the connection by calling [#connect()].
    ///   - **Keep-Alive Check:** If the connection is active, it checks if a keep-alive message
    ///     has been received within the [#keepAliveTimeout] duration. If not, it assumes
    ///     the connection is stale, closes the current WebSocket, and attempts to reconnect.
    ///   - **Error Handling:** It tracks consecutive connection failures. If the number of failures
    ///     exceeds a threshold (10), it will stop trying to reconnect and [#close()] the client
    ///     to prevent an infinite loop of failures.
    ///   - **Shutdown:** When the [#operationalState] is set to `CLOSED`, this loop
    ///     ensures the underlying executor service is forcefully shut down.
    ///
    private void asyncKeepAliveLoop() {
        String loggingPrefix = "Hub connection loop - ";
        LOG.debug(loggingPrefix + "Operational state = {}", operationalState);
        if (operationalState == OperationalState.CLOSED) {
            LOG.warn(loggingPrefix + "Hub is struggling to close properly. Will try to force close...");
            LOG.debug(loggingPrefix + "Hub executor service isShutdown: {}, isTerminated: {}",
                    executorService.isShutdown(),
                    executorService.isTerminated());
            this.close();
        } else {
            // We _should_ be connected (or in the process of establishing a connection)
            if (connectionState == State.READY || connectionState == State.DISCONNECTED) {
                // ... But we aren't. Need to take action to connect.
                LOG.warn(loggingPrefix + "Not connected to the SignalR hub. Connection state: {}. Will try to reconnect...",
                        connectionState);
                try {
                    connect();
                    errorCounter = 0;
                } catch (Exception e) {
                    errorCounter++;
                    LOG.warn(loggingPrefix + "Error connecting to hub: {}", e.toString());
                    if (errorCounter > 9) {
                        LOG.error(loggingPrefix + "Too many subsequent connections errors: {}. Will shut down the listener", 10);
                        close();
                    }
                }     
            } else {
                // We are connected.
                // Check if we have received a keep alive message recently--this indicates that the connection is alive
                if (Duration.between(lastKeepAliveMessage, Instant.now()).compareTo(keepAliveTimeout) > 0) {
                    LOG.warn(loggingPrefix + "Looks like we have a connection with the SignalR hub, but have not received a keep alive message in a while. "
                            + "Connection state: {}, duration since last keep alive message: {}. Will try to reconnect...",
                            connectionState, Duration.between(lastKeepAliveMessage, Instant.now()));
                    // It has been too long since the last keep alive message. We need to try and reconnect.
                    // In case we have an open websocket, close it
                    if (null != webSocket) webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");

                    // reconnect
                    try {
                        connect(true);
                        errorCounter = 0;
                    } catch (Exception e) {
                        errorCounter++;
                        LOG.warn(loggingPrefix + "Error connecting to hub: {}", e.toString());
                        if (errorCounter > 9) {
                            LOG.error(loggingPrefix + "Too many subsequent connections errors: {}. Will shut down the listener", 10);
                            close();
                        }
                    }
                } else {
                    // We are connected _and_ we have received a keep alive message. All is good.
                    LOG.trace(loggingPrefix + "We are connected to the SignalR hub. Operational state: {}, Connection state: {}",
                            operationalState, connectionState);
                }
            }
        }
    }

    /// Performs the SignalR negotiation handshake and establishes a WebSocket connection.
    ///
    /// This method implements the two-step connection process required by the SignalR protocol.
    /// <ol>
    ///   - **Negotiation:** It sends an initial HTTP GET request to the hub's `/negotiate`
    ///     endpoint. This request is used to agree on protocol details and obtain a unique
    ///     `connectionToken` and a session cookie from the server.
    ///   - **Connection:** If negotiation is successful, it uses the obtained token and cookie
    ///     to construct a WebSocket URI (e.g., `wss://...`). It then establishes a persistent
    ///     WebSocket connection to this URI.
    /// </ol>
    /// The method blocks execution until the WebSocket connection is fully established or an error occurs.
    /// It also sets the internal [#connectionState] to `CONNECTING` during this process.
    ///
    /// @return The fully connected [WebSocket] instance.
    /// @throws IOException if the negotiation request fails, the server returns an error, or the
    ///         WebSocket connection cannot be established.
    private WebSocket negotiateWebsocket() throws IOException {
        String loggingPrefix = "negotiateWebsocket() - ";
        setConnectionState(State.CONNECTING);

        final ObjectReader objectReader = objectMapper.reader();
        final URI negotiateURI = getBaseUri().resolve(String.format("%s?%s=%s&%s=%s",
                negotiatePath,
                connectionDataKey,
                URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                clientProtocolKey,
                clientProtocol));
        LOG.info(loggingPrefix + "Negotiating connection to {}", negotiateURI.getAuthority());
        LOG.trace(loggingPrefix + "Negotiate URI: {}", negotiateURI.toString());

        HttpRequest negotiateRequest = HttpRequest.newBuilder()
                .uri(negotiateURI)
                .GET()
                .build();

        try {
            HttpResponse<String> negotiateResponse = httpClient
                    .send(negotiateRequest, HttpResponse.BodyHandlers.ofString());
            String responseBody = negotiateResponse.body();

            LOG.debug(loggingPrefix + "Negotiate response:\n {}", negotiateResponse.toString());
            LOG.debug(loggingPrefix + "Response headers: \n{}", negotiateResponse.headers().toString());
            LOG.debug(loggingPrefix + "Response body: \n{}", responseBody);
            if (negotiateResponse.statusCode() >= 300) {
                String message = loggingPrefix +  "Failed to negotiate connection to %s. Response: %s".formatted(
                        negotiateURI.getAuthority(),
                        negotiateResponse.toString()
                );
                LOG.error(message);
                throw new IOException(message);
            }

            // Parse the response
            String connectionToken = "";
            String cookie = negotiateResponse.headers().firstValue("set-cookie").orElse("");
            LOG.debug(loggingPrefix + "Negotiate cookie: {}", cookie);
            JsonNode responseBodyRoot = objectReader.readTree(responseBody);
            if (responseBodyRoot.path("ConnectionToken").isTextual()) {
                connectionToken = responseBodyRoot.path("ConnectionToken").asText();
            } else {
                // A connection token is mandatory for the next step.
                throw new IOException(loggingPrefix + "Unable to get connection token from the SignalR service during negotiation.");
            }

            // Check keep alive timeout.
            if (responseBodyRoot.path("KeepAliveTimeout").isNumber()) {
                keepAliveTimeout = Duration.ofSeconds(responseBodyRoot.path("KeepAliveTimeout").asInt());
                LOG.debug(loggingPrefix + "Found keep alive timeout spec in connection negotiation: {}",
                        responseBodyRoot.path("KeepAliveTimeout").asText());
            } else if (responseBodyRoot.path("KeepAliveTimeout").isNull()) {
                keepAliveTimeout = Duration.ofDays(365);
                LOG.debug(loggingPrefix + "KeepAliveTimeout = null. Setting the reconnect timeout to one year.");
            }

            // Build the websocket URI. If the base URI was http, we need to use the ws scheme. Else, use wss.
            String websocketScheme = "wss";     // Default to wss / secure connection
            if (getBaseUri().getScheme().equalsIgnoreCase("http")) {
                websocketScheme = "ws";
            }

            URI wssURI = new URI(getBaseUri().toString().replaceFirst(getBaseUri().getScheme(), websocketScheme))
                    .resolve(String.format("connect?transport=webSockets&%s=%s&%s=%s&%s=%s",
                            connectionDataKey,
                            URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                            clientProtocolKey,
                            clientProtocol,
                            "connectionToken",
                            URLEncoder.encode(connectionToken, StandardCharsets.UTF_8))
                    );

            LOG.debug(loggingPrefix + "Websocket URI: {}", wssURI.toString());
            LOG.info(loggingPrefix + "Setting up websocket connection...");

            return httpClient.newWebSocketBuilder()
                    .header("User-Agent", "BestHTTP")
                    .header("Accept-Encoding", "gzip,identity")
                    .header("Cookie", cookie)
                    .connectTimeout(Duration.ofSeconds(30))
                    .buildAsync(wssURI, wssListener)
                    .join();

        } catch (Exception e) {
            LOG.error(loggingPrefix + "Error connecting to hub: {}", e.toString());
            setConnectionState(State.READY);

            throw new IOException(e);
        }
    }

    /// Processes a raw message received from the WebSocket and directs it based on the current connection state.
    ///
    /// This method acts as the central router for all incoming SignalR messages. Its behavior changes
    /// depending on whether the client is in the process of connecting or is fully connected:
    ///
    ///   - **Logging:** If message logging is enabled, it first writes the raw message to a file.
    ///   - **Connecting State:** When in the `CONNECTING` state, it waits for a SignalR
    ///     initialization message. Upon receiving it, the connection state is transitioned to `CONNECTED`.
    ///   - **Connected State:** Once `CONNECTED`, it distinguishes between keep-alive pings (which
    ///     update the [#lastKeepAliveMessage] timestamp) and actual data messages (which are passed to
    ///     [#notifySubscribers(String)] for parsing and distribution).
    ///
    /// Any unexpected messages or parsing failures will be logged as errors. A critical parsing failure
    /// will result in a [RuntimeException], which will likely terminate the connection.
    ///
    /// @param message The complete, raw message string received from the WebSocket.
    private void processMessage(String message) {
        String loggingPrefix = "processMessage() - ";

        // Capture statistics on the number of messages received
        String recordCategory = "Unknown";
        try {
            recordCategory = switch (MessageDecoder.parseSignalRMessage(message)) {
                case UnknownMessage u -> "Unknown";
                case InitMessage i -> "Init";
                case KeepAliveMessage k -> "KeepAlive";
                case GroupMembershipMessage g -> "GroupMembership";
                case HubResponseMessage h -> "HubResponse";
                case ClientMethodInvocationMessage c -> "ClientMethodInvocation";
            };
        } catch (JsonProcessingException e) {
            LOG.warn(loggingPrefix + "Error when processing received signalR message: Raw message: '{}'. Error: {}",
                    message, e.getMessage());;
        }

        recordReceivedCounter.labelValues(recordCategory).inc();

        // Store the messages on disk if logging is enabled
        if (isMessageLogEnabled()) {
            logMessage(message);
        }

        // Process the message based on the current connection state
        try {
            switch (connectionState) {
                case READY -> LOG.error(loggingPrefix + "Message received before connection has been set up. Should not happen. Message: {}",
                        message);
                case CONNECTING -> {
                    if (MessageDecoder.isInitMessage(message)) {
                        setConnectionState(State.CONNECTED);
                        LOG.info(loggingPrefix + "SignalR hub connection established over websocket.");
                    } else {
                        LOG.warn("Unrecognized message received while waiting for a SignalR initialization message. Message: {}",
                                message);
                    }
                }
                case CONNECTED -> {
                    if (MessageDecoder.isKeepAliveMessage(message)) {
                        LOG.debug(loggingPrefix + "Client in state _connected_, received keep alive message.");
                        lastKeepAliveMessage = Instant.now();
                    } else {
                        LOG.debug(loggingPrefix + "Client in state _connected_, received subscription message.");
                        notifySubscribers(message);
                    }
                }
                case DISCONNECTED -> LOG.error(loggingPrefix + "Message received while disconnected. Should not happen.");
            }
        } catch (JsonProcessingException e) {
            // This is a critical failure, as we can't understand the server.
            LOG.error(loggingPrefix + "Failed to parse JSON message from the SignalR hub. Message: '{}'", message, e);
            throw new RuntimeException("Unrecoverable JSON parsing error", e);
        } catch (Exception e) {
            // Catch any other unexpected errors.
            LOG.error(loggingPrefix + "An unexpected error occurred while processing a message from the hub.", e);
            throw new RuntimeException(e);
        }

        LOG.trace(loggingPrefix + "Received wss message:\n {}", message);
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
    /// @param rawMessage The raw JSON string received from the WebSocket.
    private void notifySubscribers(String rawMessage) {
        String loggingPrefix = "notifySubscribers() - ";
        LOG.debug(loggingPrefix + "Raw message: {}", rawMessage);
        try {
            // Need to make sure we have a registered consumer for the messages
            if (null != getConsumer()) {
                List<? extends LiveTimingRecord> messages = MessageDecoder.parseLiveTimingMessages(rawMessage);
                LOG.debug(loggingPrefix + "Parsed raw message into {} live timing messages", messages.size());
                messages.forEach(message -> getConsumer().accept(message));
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Error when processing received signalR message: Raw message: '{}'. Error: {}", rawMessage, e.getMessage());
        }
    }

    public class SignalrWssListener implements WebSocket.Listener {
        private List<CharSequence> parts = new ArrayList<>();
        private CompletableFuture<?> accumulatedMessage = new CompletableFuture<>();

        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            LOG.info("Websocket open.");
            LOG.debug("Websocket open: {}", webSocket.toString());
        }

        public CompletionStage<?> onText(WebSocket webSocket,
                                         CharSequence message,
                                         boolean last) {
            parts.add(message);
            // TODO should we move .request to the end? Should we just return null instead of completion stage?
            webSocket.request(1);
            if (last) {
                processMessage(assembleMessage(parts));
                parts = new ArrayList<>();
                accumulatedMessage.complete(null);
                CompletionStage<?> cf = accumulatedMessage;
                accumulatedMessage = new CompletableFuture<>();
                return cf;
            }
            return accumulatedMessage;
        }

        private String assembleMessage(List<CharSequence> parts) {
            return parts.stream()
                    .map(CharSequence::toString)
                    .collect(Collectors.joining());
        }

        public CompletionStage<?> onClose(WebSocket webSocket,
                                          int statusCode,
                                          String reason) {
            setConnectionState(State.READY);
            LOG.info("Websocket closed. Status code: {}. Reason: {}",
                    statusCode,
                    reason);
            return null;
        }

        public void onError(WebSocket webSocket, Throwable error) {
            setConnectionState(State.DISCONNECTED);
            LOG.warn("Websocket error: \n {}", error.toString());
        }
    }

    /// Represents the granular, low-level status of the underlying WebSocket connection.
    /// This enum tracks the different phases of establishing and maintaining a connection
    /// to the SignalR hub. It is managed internally and is distinct from
    /// [OperationalState], which reflects the user's high-level intent for the client.
    enum State {
        /// The client is not connected but is ready to initiate a new connection.
        /// This is the initial state, and also the state after a WebSocket is cleanly closed.
        /// From this state, a new connection attempt can begin.
        READY (0),
        /// The client is in the process of establishing a connection. This includes
        /// the HTTP negotiation phase and waiting for the WebSocket to become fully
        /// open and receive the SignalR initialization message.
        CONNECTING (1),
        /// The WebSocket connection is established, and the SignalR protocol handshake
        /// is complete. The client is now able to send and receive data messages.
        CONNECTED (2),
        /// The WebSocket connection has been lost due to an error. The background
        /// keep-alive loop will attempt to reconnect when the client is in this state.
        DISCONNECTED (3);

        private final int statusValue;

        public int getStatusValue() {
            return statusValue;
        }

        State(int statusValue) {
            this.statusValue = statusValue;
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
