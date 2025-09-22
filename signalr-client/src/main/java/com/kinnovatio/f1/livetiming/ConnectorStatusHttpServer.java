package com.kinnovatio.f1.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.kinnovatio.signalr.messages.LiveTimingMessage;
import org.apache.commons.lang3.StringUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An embedded HTTP server that provides status information about the F1 live timing connector.
 * <p>
 * This server exposes two endpoints:
 * <ul>
 *     <li>{@code /}: Serves static files from the classpath's {@code /static} directory.</li>
 *     <li>{@code /status}: Provides a JSON response with detailed status information about the connector,
 *     including connection state, session details, and message rate statistics.</li>
 * </ul>
 * The server should be started via {@link #start()} and properly shut down using {@link #stop()}
 * to release the network port.
 */
public class ConnectorStatusHttpServer {
    /** The logger for this class. */
    private static final Logger LOG = LoggerFactory.getLogger(ConnectorStatusHttpServer.class);
    /** A shared {@link ObjectMapper} instance for JSON serialization. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** The port number the HTTP server will listen on. */
    private final int port;
    /** The socket address for the HTTP server. */
    private final InetSocketAddress serverAddress;
    /** The underlying {@link HttpServer} instance. It is null until {@link #start()} is called. */
    private HttpServer server;

    /**
     * Private constructor to create a server instance on a specific port.
     *
     * @param port The port number for the server to listen on.
     */
    private ConnectorStatusHttpServer(int port) {
        this.port = port;
        this.serverAddress = new InetSocketAddress(port);
    }

    /**
     * Private constructor to create a server instance on the default port (8080).
     */
    private ConnectorStatusHttpServer() {
        this(8080); // set default port to 8080
    }

    /**
     * Creates a new {@link ConnectorStatusHttpServer} instance that will listen on the default port (8080).
     *
     * @return A new server instance.
     */
    public static ConnectorStatusHttpServer create() {
        return new ConnectorStatusHttpServer();
    }

    /**
     * Creates a new {@link ConnectorStatusHttpServer} instance that will listen on the specified port.
     *
     * @param port The port number for the server.
     * @return A new server instance.
     */
    public static ConnectorStatusHttpServer on(int port) {
        return new ConnectorStatusHttpServer(port);
    }

    /**
     * Starts the HTTP server.
     * <p>
     * If the server is not already initialized, it creates the server, sets up the context handlers
     * for static files and the status endpoint, and then starts it. If the server is already running,
     * this method has no effect.
     *
     * @throws IOException if an I/O error occurs when creating or starting the server.
     * @throws URISyntaxException if there is an error parsing the static resource path.
     */
    public void start() throws IOException, URISyntaxException {
        if (server == null) {
            Path staticResourceRoot = Paths.get("/static");
            HttpHandler fileHandler = SimpleFileServer.createFileHandler(staticResourceRoot);
            server = HttpServer.create(serverAddress, port);
            server.createContext("/", fileHandler);
            server.createContext("/status", new StatusDataHandler());
            LOG.info("HTTP server: Ready to serve files from {}", staticResourceRoot);
        }
        server.start();
        LOG.info("HTTP Server: Started on port {}", port);
    }

    /**
     * Stops the HTTP server immediately.
     * <p>
     * If the server is running, it is stopped, and the underlying instance is set to null.
     * If the server is not running, this method does nothing.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("HTTP Server: Stopped");
            server = null;
        } else {
            LOG.info("HTTP Server: Not running");
        }
    }

    /**
     * An {@link HttpHandler} that serves connector status data as a JSON response.
     * <p>
     * This handler responds to GET requests on the {@code /status} path. It gathers status
     * information from the main {@link Client} and formats it into a comprehensive JSON object.
     */
    private static class StatusDataHandler implements HttpHandler {
        /**
         * Default constructor for the status data handler.
         */
        public StatusDataHandler() {
        }

        /**
         * Handles an incoming HTTP request for the status endpoint.
         * <p>
         * This method only accepts GET requests. It retrieves the current connector and session status,
         * builds a JSON response, and sends it to the client.
         *
         * @param exchange The {@link HttpExchange} representing the client request and server response.
         * @throws IOException if an I/O error occurs while handling the request or sending the response.
         */
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    exchange.sendResponseHeaders(405, -1); //405 method not allowed
                    return;
                }
                ConnectorStatus connectorStatus = Client.getConnectorStatus();

                String defaultSessionInfoString = "No session info available";

                SessionInfo sessionInfo = Client.getSessionInfo()
                        .orElse(new SessionInfo(defaultSessionInfoString, defaultSessionInfoString, defaultSessionInfoString,
                                defaultSessionInfoString, defaultSessionInfoString, defaultSessionInfoString));

                // build the response json tree model
                ObjectNode rootNode = objectMapper.createObjectNode();
                rootNode.put("connectorStatus", connectorStatus.connectorState());
                rootNode.put("connectorLastSessionCheckEpoch", connectorStatus.lastSessionCheck().getEpochSecond());
                rootNode.put("connectorOperationalStatus", Client.getHubConnection().getOperationalState());
                rootNode.put("connectorConnectionStatus", Client.getHubConnection().getConnectionState());
                rootNode.put("sessionStatus", sessionInfo.status());
                rootNode.put("archiveStatus", sessionInfo.archiveStatus());
                rootNode.put("meetingName", sessionInfo.meetingName());
                rootNode.put("sessionType", sessionInfo.type());
                rootNode.put("sessionStartDate", sessionInfo.startDate());
                rootNode.put("sessionEndDate", sessionInfo.endDate());

                // add messages from queue
                ArrayNode messages = objectMapper.createArrayNode();
                for (LiveTimingMessage message : connectorStatus.messages()) {
                    ObjectNode messageRoot = objectMapper.createObjectNode();
                    messageRoot.put("timestampEpoch", message.timestamp().toEpochSecond());
                    messageRoot.put("category", message.category());
                    messageRoot.put("messageShort", StringUtils.truncate(message.message(), 100));
                    messageRoot.put("message", message.message());
                    messages.add(messageRoot);
                }
                rootNode.set("messages", messages);

                // add message rate per second
                ArrayNode messageRatePerSecond = objectMapper.createArrayNode();
                for (RateTuple rateTuple : connectorStatus.messageRatePerSecond()) {
                    ObjectNode tupleRoot = objectMapper.createObjectNode();
                    tupleRoot.put("timestampEpoch", rateTuple.instant().getEpochSecond());
                    tupleRoot.put("count", rateTuple.count());
                    messageRatePerSecond.add(tupleRoot);
                }
                rootNode.set("messageRatePerSecond", messageRatePerSecond);

                // add message rate per minute
                ArrayNode messageRatePerMinute = objectMapper.createArrayNode();
                for (RateTuple rateTuple : connectorStatus.messageRatePerMinute()) {
                    ObjectNode tupleRoot = objectMapper.createObjectNode();
                    tupleRoot.put("timestampEpoch", rateTuple.instant().getEpochSecond());
                    tupleRoot.put("count", rateTuple.count());
                    messageRatePerMinute.add(tupleRoot);
                }
                rootNode.set("messageRatePerMinute", messageRatePerMinute);

                String jsonResponse = rootNode.toString();
                byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            } catch (IOException e) {
                LOG.warn("Error when updating connector status: {}", e.toString());
                throw e;
            }
        }
    }
}
