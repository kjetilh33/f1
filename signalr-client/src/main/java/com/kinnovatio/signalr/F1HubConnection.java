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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@AutoValue
public abstract class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String baseUrl = "https://livetiming.formula1.com/signalr";
    private static final String wssUrl = "wss://livetiming.formula1.com/signalr/connect";
    private static final String negotiatePath = "negotiate";
    private static final String clientProtocolKey = "clientProtocol";
    private static final String clientProtocol = "1.5";
    private static final String connectionDataKey = "connectionData";
    private static final String connectionData = """
                [{"name": "streaming"}]
                """;

    private State connectionState = State.READY;
    private Instant lastHeartbeat = null;

    private static F1HubConnection.Builder builder() {
        return new AutoValue_F1HubConnection.Builder();
    }

    public static F1HubConnection create() {
        return F1HubConnection.builder().build();
    }


    public WebSocket negotiateWebsocket() throws IOException, URISyntaxException {
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

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
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

            URI wssURI = new URI(String.format(wssUrl + "?transport=webSockets&%s=%s&%s=%s&%s=%s",
                    connectionDataKey,
                    URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                    clientProtocolKey,
                    clientProtocol,
                    "connectionToken",
                    URLEncoder.encode(connectionToken, StandardCharsets.UTF_8)));

            LOG.debug("Websocket URI: {}", wssURI.toString());

            LOG.info("Setting up websocket connection...");
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .header("User-Agent", "BestHTTP")
                    .header("Accept-Encoding", "gzip,identity")
                    .header("Cookie", cookie)
                    .buildAsync(wssURI, new F1HubConnection.SignalrWssListener())
                    .join();

            return webSocket;

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
                        lastHeartbeat = Instant.now();
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
            LOG.info("Websocket open: {}", webSocket.toString());
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

    @AutoValue.Builder
    abstract static class Builder {
        //abstract Builder setClient(CogniteClient value);

        abstract F1HubConnection build();
    }
}
