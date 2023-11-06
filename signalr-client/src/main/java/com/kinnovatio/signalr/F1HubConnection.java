package com.kinnovatio.signalr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class F1HubConnection {
    private static final Logger LOG = LoggerFactory.getLogger(F1HubConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl = "https://livetiming.formula1.com/signalr";
    private String wssUrl = "wss://livetiming.formula1.com/signalr/connect";
    private final String negotiatePath = "negotiate";
    private final String clientProtocolKey = "clientProtocol";
    private final String clientProtocol = "1.5";
    private final String connectionDataKey = "connectionData";
    private final String connectionData = """
                [{"name": "streaming"}]
                """;


    public WebSocket negotiateWebsocket() throws IOException, URISyntaxException {
        final ObjectReader objectReader = objectMapper.reader();

        URI negotiateURI = new URI(String.format(baseUrl + "/%s?%s=%s&%s=%s",
                negotiatePath,
                connectionDataKey,
                URLEncoder.encode(connectionData, StandardCharsets.UTF_8),
                clientProtocolKey,
                clientProtocol));
        LOG.info("Negotiate URI: {}", negotiateURI.toString());

        HttpRequest negotiateRequest = HttpRequest.newBuilder()
                .uri(negotiateURI)
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> negotiateResponse = httpClient
                    .send(negotiateRequest, HttpResponse.BodyHandlers.ofString());
            String responseBody = negotiateResponse.body();

            LOG.info("Negotiate response:\n {}", negotiateResponse.toString());
            LOG.info("Response headers: \n{}", negotiateResponse.headers().toString());
            LOG.info("Response body: \n{}", responseBody);

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

            LOG.info("Websocket URI: {}", wssURI.toString());

            LOG.info("Set up websocket connection...");
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .header("User-Agent", "BestHTTP")
                    .header("Accept-Encoding", "gzip,identity")
                    .header("Cookie", cookie)
                    .buildAsync(wssURI, new Client.SignalrWssListener())
                    .join();

            return webSocket;

        } catch (Exception e) {
            LOG.error("Error connecting to hub: {}", e.toString());
            throw new IOException(e);
        }

    }


    public static class SignalrWssListener implements WebSocket.Listener {
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
                processMessage(parts);
                parts = new ArrayList<>();
                accumulatedMessage.complete(null);
                CompletionStage<?> cf = accumulatedMessage;
                accumulatedMessage = new CompletableFuture<>();
                return cf;
            }
            return accumulatedMessage;
        }

        private void processMessage(List<CharSequence> parts) {
            String message = parts.stream()
                    .map(CharSequence::toString)
                    .collect(Collectors.joining());

            LOG.info("Received wss message:\n {}", message);
        }

        public CompletionStage<?> onClose(WebSocket webSocket,
                                          int statusCode,
                                          String reason) {
            LOG.info("Websocket closed. Status code: {}. Reason: {}",
                    statusCode,
                    reason);
            return null;
        }

        public void onError(WebSocket webSocket, Throwable error) {
            LOG.info("Websocket error: \n {}", error.toString());
        }
    }
}
