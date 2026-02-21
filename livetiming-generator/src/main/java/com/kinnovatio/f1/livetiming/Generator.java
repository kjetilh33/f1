package com.kinnovatio.f1.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class Generator {
    private static final Logger LOG = LoggerFactory.getLogger(Generator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /// Flag to control whether messages are published to Kafka.
    /// Loaded from the "target.kafka.enable" configuration property.
    private static final boolean enableKafka =
            ConfigProvider.getConfig().getValue("target.kafka.enable", Boolean.class);

    /// Flag to control whether the Prometheus metrics server is started.
    /// Loaded from the "metrics.enable" configuration property.
    private static final boolean enableMetrics =
            ConfigProvider.getConfig().getValue("metrics.enable", Boolean.class);

    /// The main entry point for the application.
    /// It initializes and runs the client, catching any unrecoverable exceptions.
    ///
    /// @param args Command line arguments (not used).
    public static void main(String[] args) {
        try {
            // Execute the main logic
            run();

        } catch (Exception e) {
            LOG.error("Unrecoverable error. Will exit. {}", e.toString());
            //errorGauge.inc();
        }
    }

    /// Initializes and starts all application components.
    /// This includes the SignalR connection, the status HTTP server, the metrics server,
    /// and the background task for connection management.
    /// @throws Exception if initialization of the SignalR client fails.
    private static void run() throws Exception {
        LOG.info("Starting container...");


    }
}
