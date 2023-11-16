package com.kinnovatio.signalr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.auto.value.AutoValue;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.nio.ByteBuffer;

@AutoValue
public abstract class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Path defaultPathMessageLog = Path.of("./received-messages.log");

    private static final String baseUrl = "https://livetiming.formula1.com/signalr";
    private static final String wssUrl = "wss://livetiming.formula1.com/signalr/connect";
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
    private SignalrWssListener wssListener = new SignalrWssListener();

    private static F1HubConnection.Builder builder() {
        return new AutoValue_F1HubConnection.Builder()
                .setMessageLogEnabled(false);
    }

    public static F1HubConnection create() {
        return F1HubConnection.builder().build();
    }

    protected abstract Builder toBuilder();

    public abstract boolean isMessageLogEnabled();

    public F1HubConnection enableMessageLogging(boolean enable) {
        return toBuilder().setMessageLogEnabled(enable).build();
    }

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

    public void close() {
        operationalState = OperationalState.CLOSED;
        executorService.shutdown();
    }

    private void asyncKeepAliveLoop() {
        if (operationalState == OperationalState.CLOSED) {
            LOG.warn("Hub is struggling to close properly. Will try to force close...");
            LOG.debug("Hub executor service isShutdown: {}, isTerminated: {}",
                    executorService.isShutdown(),
                    executorService.isTerminated());
            executorService.shutdownNow();
        } else {
            LOG.debug("hub connection--just checking loop...");
            //webSocket.sendPing(ByteBuffer.wrap("ping".getBytes()));
        }
    }

    private WebSocket negotiateWebsocket() throws IOException, URISyntaxException {
        connectionState = State.CONNECTING;
        final ObjectReader objectReader = objectMapper.reader();

        URI negotiateURI = new URI(String.format(baseUrl + "/%s?%s=%s&%s=%s",
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

            URI wssURI = new URI(String.format(wssUrl + "?transport=webSockets&%s=%s&%s=%s&%s=%s",
                    connectionDataKey,
                    URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                    clientProtocolKey,
                    clientProtocol,
                    "connectionToken",
                    URLEncoder.encode(connectionToken, StandardCharsets.UTF_8)));

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
                case READY -> LOG.error("Message received before connection is ready. Should not happen.");
                case CONNECTING -> {
                    if (MessageDecoder.isInitMessage(message)) {
                        connectionState = State.CONNECTED;
                        LOG.info("Websocket connection established.");
                    }
                }
                case CONNECTED -> {
                    LOG.debug("Connected, message received.");
                    if (MessageDecoder.isKeepAliveMessage(message)) {
                        lastKeepAliveMessage = Instant.now();
                    } else {
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

    private void notifySubscribers(String message) {
        LOG.info("Notify subscribers. Message: {}", message);
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

        abstract F1HubConnection build();
    }
}
