package com.kinnovatio.f1.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.livetiming.source.FileDataFeed;
import com.kinnovatio.signalr.messages.LiveTimingHubResponseMessage;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private static final boolean printMessages =
            ConfigProvider.getConfig().getOptionalValue("printMessages", Boolean.class).orElse(false);

    private static final int jobDurationSeconds =
            ConfigProvider.getConfig().getOptionalValue("jobDurationSeconds", Integer.class).orElse(7200);

    /// The main entry point for the application.
    /// It initializes and runs the client, catching any unrecoverable exceptions.
    ///
    /// @param args Command line arguments (not used).
    public static void main(String[] args) {
        try {
            // Execute the main logic
            Duration runDuration = Duration.ofSeconds(jobDurationSeconds);
            if (args.length == 1) {
                runDuration = Duration.ofSeconds(Integer.parseInt(args[0]));
            }
            run(runDuration);

        } catch (Exception e) {
            LOG.error("Unrecoverable error. Will exit. {}", e.toString());
            System.exit(1);
        }
    }

    /// Initializes and starts all application components.
    /// This includes the SignalR connection, the status HTTP server, the metrics server,
    /// and the background task for connection management.
    /// @throws Exception if initialization of the SignalR client fails.
    private static void run(Duration runDuration) throws Exception {
        LOG.info("Starting container...");
        LOG.info("Will generate data for {} before shutting down.");
        if (enableKafka) {
            LOG.info("The data will be published to Kafka");
        }

        FileDataFeed fileDataFeed = new FileDataFeed(Generator::processMessage);
        fileDataFeed.start();
        Thread.sleep(runDuration);
        LOG.info("Finished job...");
    }

    /// The primary callback method for processing all data received from the [FileDataFeed].
    ///
    /// @param message The [LiveTimingRecord] received from the hub, which can be a single message
    ///                or a container for multiple messages.
    private static void processMessage(LiveTimingRecord message) {
        LOG.debug("Received live timing record: {}", message);

        switch (message) {
            case LiveTimingHubResponseMessage hubResponse -> {
                List<LiveTimingMessage> messages = hubResponse.messages();
                messages.forEach(Generator::processLiveTimingMessage);
            }
            case LiveTimingMessage timingMessage -> {
                processLiveTimingMessage(timingMessage);
            }
        }
    }

    /// Processes an individual [LiveTimingMessage].
    /// This method forwards the message to Kafka (if enabled)
    ///
    /// @param message The live timing message to process.
    private static void processLiveTimingMessage(LiveTimingMessage message) {
        if (printMessages) {
            IO.println("Generated message: " + message);
        }
        if (enableKafka) {
            KafkaProducer.getInstance().publish(message);
        }
    }
}
