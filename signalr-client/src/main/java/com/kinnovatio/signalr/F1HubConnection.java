package com.kinnovatio.signalr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.auto.value.AutoValue;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.MessageDecoder;
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

@AutoValue
public abstract class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path defaultPathMessageLog = Path.of("./received-messages.log");

    private static final String baseUrl = "https://livetiming.formula1.com/signalr/";
    private static final String negotiatePath = "negotiate";
    private static final String clientProtocolKey = "clientProtocol";
    private static final String clientProtocol = "1.5";
    private static final String connectionDataKey = "connectionData";
    private static final String connectionData = """
                [{"name": "streaming"}]
                """;

    private static final String[] dataStreams = {"Heartbeat", "CarData.z", "Position.z",
            "ExtrapolatedClock", "TopThree", "RcmSeries",
            "TimingStats", "TimingAppData",
            "WeatherData", "TrackStatus", "DriverList",
            "RaceControlMessages", "SessionInfo",
            "SessionData", "LapCount", "TimingData"};

    private State connectionState = State.READY;
    private OperationalState operationalState = OperationalState.CLOSED;
    private ScheduledExecutorService executorService = null;

    private Instant lastKeepAliveMessage = null;
    private Duration keepAliveTimeout = Duration.ofSeconds(30);
    private HttpClient httpClient = null;
    private WebSocket webSocket = null;
    private final SignalrWssListener wssListener = new SignalrWssListener();
    private int errorCounter = 0;

    private static F1HubConnection.Builder builder() {
        return new AutoValue_F1HubConnection.Builder()
                .setMessageLogEnabled(false);
    }

    public static F1HubConnection create() {
        try {
            return F1HubConnection.of(baseUrl);
        } catch (URISyntaxException e) {
            LOG.error("Unable to create connection to the default base URL: {}", e.toString());
            throw new RuntimeException(e);
        }
    }

    public static F1HubConnection of(String baseUri) throws URISyntaxException {
        return F1HubConnection.of(new URI(baseUri));
    }

    public static F1HubConnection of(URI baseUri) {
        return F1HubConnection.builder()
                .setBaseUri(baseUri)
                .build();
    }

    protected abstract Builder toBuilder();

    public abstract URI getBaseUri();

    @Nullable
    public abstract Consumer<LiveTimingMessage> getConsumer();
    public abstract boolean isMessageLogEnabled();

    public F1HubConnection enableMessageLogging(boolean enable) {
        return toBuilder().setMessageLogEnabled(enable).build();
    }

    public F1HubConnection withConsumer(Consumer<LiveTimingMessage> consumer) {
        return toBuilder().setConsumer(consumer).build();
    }

    /**
     * Initiate a SignalR connection. This method will try to setup a connection over websocket. Once the
     * connection is ready, you have to call {@link #subscribeToAll()} to start receiving live timing
     * messages.
     *
     * @return {@code true} if the connection was set up successfully. {@code false} otherwise.
     * @throws IOException if something goes wrong at the network layer.
     * @throws URISyntaxException if the target URI is invalid.
     * @throws InterruptedException if the working thread gets interrupted.
     */
    public boolean connect() throws IOException, URISyntaxException, InterruptedException {
        if (operationalState == OperationalState.OPEN) {
            LOG.warn("The connection is already open. Connect() has no effect.");
            return false;
        }

        // In case we have an open websocket, close it
        if (null != webSocket) webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");

        // Check if we need to instantiate the http client
        if (null == httpClient) httpClient = HttpClient.newHttpClient();

        try {
            Instant startInstant = Instant.now();
            connectionState = State.READY;
            operationalState = OperationalState.OPEN;
            webSocket = negotiateWebsocket();

            // try for 20 seconds to establish a connection
            while (Duration.between(startInstant, Instant.now()).compareTo(Duration.ofSeconds(20)) < 1
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
            executorService.scheduleAtFixedRate(this::asyncKeepAliveLoop, 1, 1, TimeUnit.SECONDS);

        } catch (Exception e) {
            operationalState = OperationalState.CLOSED;
            connectionState = State.READY;
            webSocket = null;
            throw e;
        }

        return true;
    }

    /**
     * Start subscribing to the live timing data (all data streams/types)
     */
    public void subscribeToAll() {
        if (operationalState != OperationalState.OPEN || connectionState != State.CONNECTED) {
            LOG.warn("The connection is not ready. Operational state = {}, connection state = {}",
                    operationalState, connectionState);

            return;
        }
        final String hub = "Streaming";
        final String method = "Subscribe";
        final List<Object> arguments = List.of(List.of(dataStreams));
        final int identifier = 1;
        try {
            webSocket.sendText(MessageDecoder.toMessageJson(hub, method, arguments, identifier), true);
        } catch (Exception e) {
            LOG.warn("Failed to start subscription: {}", e.toString());
        }
        
    }

    public void close() {
        operationalState = OperationalState.CLOSED;
        executorService.shutdown();
    }

    private void asyncKeepAliveLoop() {
        String loggingPrefix = "Hub connection loop - ";
        LOG.debug(loggingPrefix + "Operational state = {}", operationalState);
        if (operationalState == OperationalState.CLOSED) {
            LOG.warn(loggingPrefix + "Hub is struggling to close properly. Will try to force close...");
            LOG.debug(loggingPrefix + "Hub executor service isShutdown: {}, isTerminated: {}",
                    executorService.isShutdown(),
                    executorService.isTerminated());
            executorService.shutdownNow();
        } else {
            if (connectionState != State.CONNECTING && connectionState != State.CONNECTED) {
                LOG.debug(loggingPrefix + "Hub connection state: {}", connectionState);
                try {
                    connect();
                    errorCounter = 0;
                } catch (Exception e) {
                    errorCounter++;
                    LOG.warn("Error connecting to hub: {}", e.toString());
                    if (errorCounter > 9) {
                        LOG.error("Too many subsequent connections errors: {}. Will shut down the listener", 10);
                        close();
                    }
                }     
            } else {
                // Check if we have received a keep alive message recently
                if (Duration.between(lastKeepAliveMessage, Instant.now()).compareTo(keepAliveTimeout) > 0) {
                    // It has been too long since the last keep alive message. We need to try and reconnect.
                    // In case we have an open websocket, close it
                    if (null != webSocket) webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");

                    // reconnect
                    try {
                        connect();
                        errorCounter = 0;
                    } catch (Exception e) {
                        errorCounter++;
                        LOG.warn("Error connecting to hub: {}", e.toString());
                        if (errorCounter > 9) {
                            LOG.error("Too many subsequent connections errors: {}. Will shut down the listener", 10);
                            close();
                        }
                    }
                }
            }
        }
    }

    private WebSocket negotiateWebsocket() throws IOException, URISyntaxException {
        connectionState = State.CONNECTING;
        final ObjectReader objectReader = objectMapper.reader();

        URI negotiateURI = getBaseUri().resolve(String.format("%s?%s=%s&%s=%s",
                negotiatePath,
                connectionDataKey,
                URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                clientProtocolKey,
                clientProtocol));
        LOG.info("Negotiating connection to {}", negotiateURI.getAuthority());
        LOG.trace("Negotiate URI: {}", negotiateURI.toString());

        HttpRequest negotiateRequest = HttpRequest.newBuilder()
                .uri(negotiateURI)
                .GET()
                .build();

        try {
            HttpResponse<String> negotiateResponse = httpClient
                    .send(negotiateRequest, HttpResponse.BodyHandlers.ofString());
            String responseBody = negotiateResponse.body();

            LOG.debug("Negotiate response:\n {}", negotiateResponse.toString());
            LOG.debug("Response headers: \n{}", negotiateResponse.headers().toString());
            LOG.debug("Response body: \n{}", responseBody);
            if (negotiateResponse.statusCode() >= 300) {
                String message = "Failed to negotiate connection to %s. Response: %s".formatted(
                        negotiateURI.getAuthority(),
                        negotiateResponse.toString()
                );
                LOG.error(message);
                throw new IOException(message);
            }

            // Parse the response
            String connectionToken = "";
            String cookie = negotiateResponse.headers().firstValue("set-cookie").orElse("");
            LOG.debug("Negotiate cookie: {}", cookie);
            JsonNode responseBodyRoot = objectReader.readTree(responseBody);
            if (responseBodyRoot.path("ConnectionToken").isTextual()) {
                connectionToken = responseBodyRoot.path("ConnectionToken").asText();
            } else {
                throw new RuntimeException("Unable to get connection token to the SignalR service.");
            }
            if (responseBodyRoot.path("KeepAliveTimeout").isNumber()) {
                keepAliveTimeout = Duration.ofSeconds(responseBodyRoot.path("KeepAliveTimeout").asInt());
                LOG.debug("Found keep alive timeout spec in connection negotiation: {}",
                        responseBodyRoot.path("KeepAliveTimeout").asText());
            } else if (responseBodyRoot.path("KeepAliveTimeout").isNull()) {
                keepAliveTimeout = Duration.ofDays(365);
                LOG.debug("KeepAliveTimeout = null. Setting the reconnect timeout to one year.");
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

            LOG.debug("Websocket URI: {}", wssURI.toString());
            LOG.info("Setting up websocket connection...");

            return httpClient.newWebSocketBuilder()
                    .header("User-Agent", "BestHTTP")
                    .header("Accept-Encoding", "gzip,identity")
                    .header("Cookie", cookie)
                    .connectTimeout(Duration.ofSeconds(30))
                    .buildAsync(wssURI, wssListener)
                    .join();

        } catch (Exception e) {
            LOG.error("Error connecting to hub: {}", e.toString());
            connectionState = State.READY;
            throw new IOException(e);
        }
    }

    /*
    Process a received wss message.
     */
    private void processMessage(String message) {
        if (isMessageLogEnabled()) {
            try {
                Files.writeString(defaultPathMessageLog, message + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.warn("Failed to write message to log file: {}", e.toString());
            }
        }
        try {
            switch (connectionState) {
                case READY -> LOG.error("Message received before connection has been set up. Should not happen.");
                case CONNECTING -> {
                    if (MessageDecoder.isInitMessage(message)) {
                        connectionState = State.CONNECTED;
                        LOG.info("SignalR hub connection established over websocket.");
                    }
                }
                case CONNECTED -> {
                    if (MessageDecoder.isKeepAliveMessage(message)) {
                        lastKeepAliveMessage = Instant.now();
                        LOG.debug("Client in state _connected_, received keep alive message.");
                    } else {
                        LOG.debug("Client in state _connected_, received subscription message.");
                        notifySubscribers(message);
                    }
                }
                case DISCONNECTED -> LOG.debug("Message received while disconnected. Should not happen.");
            }
        } catch (Exception e) {
            LOG.error("Failed parsing message from the hub: {}", e.toString());
            throw new RuntimeException(e);
        }

        LOG.trace("Received wss message:\n {}", message);
    }

    private void notifySubscribers(String rawMessage) {
        String loggingPrefix = "notifySubscribers() - ";
        LOG.debug(loggingPrefix + "Raw message: {}", rawMessage);
        try {
            // Need to make sure we have a registered consumer for the messages
            if (null != getConsumer()) {
                List<LiveTimingMessage> messages = MessageDecoder.parseLiveTimingMessages(rawMessage);
                LOG.debug(loggingPrefix + "Parsed raw message into {} live timing messages", messages.size());
                messages.forEach(message -> getConsumer().accept(message));
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Error when processing received signalR message: {}", e.toString());
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
            connectionState = State.READY;
            LOG.info("Websocket closed. Status code: {}. Reason: {}",
                    statusCode,
                    reason);
            return null;
        }

        public void onError(WebSocket webSocket, Throwable error) {
            connectionState = State.DISCONNECTED;
            LOG.warn("Websocket error: \n {}", error.toString());
        }
    }

    enum State {
        READY,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    enum OperationalState {
        CLOSED,
        OPEN
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setMessageLogEnabled(boolean value);
        abstract Builder setBaseUri(URI value);
        abstract Builder setConsumer(Consumer<LiveTimingMessage> value);

        abstract F1HubConnection build();
    }
}
