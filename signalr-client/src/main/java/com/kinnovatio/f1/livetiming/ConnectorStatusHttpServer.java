package com.kinnovatio.f1.livetiming;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConnectorStatusHttpServer {
    // TODO: Add a simple HTTP server that exposes the connector status (e.g. connected, disconnected, etc.)
    // This can be used by Kubernetes to determine if the connector is healthy.
    // The HTTP server should be started in a separate thread.
    // The HTTP server should expose a /health endpoint that returns 200 OK if the connector is healthy.
    // The HTTP server should expose a /metrics endpoint that returns the Prometheus metrics.
    // The HTTP server should be configurable (e.g. port, etc.).
    // The HTTP server should be stopped when the connector is stopped.
    // The HTTP server should be started when the connector is started.

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorStatusHttpServer.class);
    private final int port;
    private final InetSocketAddress serverAddress;
    private HttpServer server;


    private ConnectorStatusHttpServer(int port) {
        this.port = port;
        this.serverAddress = new InetSocketAddress(port);
    }

    private ConnectorStatusHttpServer() {
        this(8080); // set default port to 8080
    }

    public static ConnectorStatusHttpServer create() {
        return new ConnectorStatusHttpServer();
    }

    public static ConnectorStatusHttpServer on(int port) {
        return new ConnectorStatusHttpServer(port);
    }

    public void start() throws IOException, URISyntaxException {
        if (server == null) {
            Path staticResourceRoot = Paths.get("/static");
            server = SimpleFileServer.createFileServer(serverAddress, staticResourceRoot, SimpleFileServer.OutputLevel.INFO);
            LOG.info("HTTP server: Ready to serve files from {}", staticResourceRoot);
        }
        server.start();
        LOG.info("HTTP Server: Started on port {}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("HTTP Server: Stopped");
            server = null;
        } else {
            LOG.info("HTTP Server: Not running");
        }
    }
}
