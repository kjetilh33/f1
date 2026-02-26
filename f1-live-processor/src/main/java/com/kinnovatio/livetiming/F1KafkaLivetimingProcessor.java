package com.kinnovatio.livetiming;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.Set;

/// Processor for F1 live timing messages from Kafka.
/// This class is responsible for:
/// - Consuming messages from the `f1-live-raw` Kafka topic.
/// - Filtering out unwanted message categories (e.g., "Heartbeat").
/// - Storing valid messages into a PostgreSQL database.
/// - Emitting status updates to the `status-out` channel.
/// It uses `AgroalDataSource` for database interactions and `Micrometer` for metrics.
@ApplicationScoped
public class F1KafkaLivetimingProcessor {
    private static final Logger LOG = Logger.getLogger(F1KafkaLivetimingProcessor.class);

    private static final Set<String> excludeCategories = Set.of("Heartbeat");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;


    @Inject
    @Channel("livetiming-out")
    Emitter<Record<String, String>> livetimingOutEmitter;

    /// Initializes the processor on startup.
    /// This method is triggered by the `StartupEvent`. It logs the startup configuration
    /// and ensures that the necessary database table exists.
    ///
    /// @param ev The startup event.
    public void onStartup(@Observes StartupEvent ev) {
        LOG.infof("Starting the live timing message processor...");

        LOG.infof("The message processor is ready. Waiting for live timing messages...");
    }



    /// Processes a batch of Kafka records and stores them in the database.
    /// This method is annotated with `@Incoming("f1-live-raw")` to consume messages from Kafka.
    /// It performs the following steps:
    /// 1. Deserializes the JSON message.
    /// 2. Filters out empty messages or excluded categories.
    /// 3. Inserts valid messages into the database using JDBC batch processing.
    /// 4. Updates metrics for discarded and stored records.
    /// 5. Emits the message to the `status-out` channel.
    /// If an error occurs during processing, the method retries up to 5 times with a delay.
    ///
    /// @param record The Kafka consumer record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-in")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    public void process(Record<String, String> record) throws Exception {
        try {
            LiveTimingMessage message = objectMapper.readValue(record.value(), LiveTimingMessage.class);

            if (message.message().isEmpty() || excludeCategories.contains(message.category())) {
                Counter.builder("livetiming_router_processor_record_discarded_total")
                        .description("Total number of live timing records discarded by the router.")
                        .tag("category" , message.category())
                        .register(registry)
                        .increment();

                LOG.debugf("Discarded record >> offset = %d, key = %s, value = %s%n", record.key(), record.value());
            } else {
                // The message is ok to distribute
                livetimingOutEmitter.send(record);
            }

        } catch (JsonParseException e) {
            LOG.warnf("Failed parsing livetiming record: %s. Record content: %s", e, record.value());
        }
    }
}
