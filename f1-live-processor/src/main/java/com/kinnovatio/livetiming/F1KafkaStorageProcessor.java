package com.kinnovatio.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Set;

/// Processor for F1 live timing messages from Kafka.
/// This class is responsible for:
/// - Consuming messages from the `f1-live-raw` Kafka topic.
/// - Filtering out unwanted message categories (e.g., "Heartbeat").
/// - Storing valid messages into a PostgreSQL database.
/// - Emitting status updates to the `status-out` channel.
/// It uses `AgroalDataSource` for database interactions and `Micrometer` for metrics.
@ApplicationScoped
public class F1KafkaStorageProcessor {
    private static final Logger LOG = Logger.getLogger(F1KafkaStorageProcessor.class);

    @ConfigProperty(name = "app.livetiming.table")
    String livetimingTable;

    private static final Set<String> excludeCategories = Set.of("Heartbeat");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    MeterRegistry registry;


    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("status-out")
    Emitter<String> statusEmitter;

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
    /// @param records The batch of Kafka consumer records.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-storage")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void toStorage(ConsumerRecords<String, String> records) throws Exception {
        LOG.infof("Livetiming messages received on f1-live-raw-storage channel. Number of records: %d", records.count());
        final int batchSize = 1000;
        int recordCount = 0;

        String sql = """
                INSERT INTO %s (category, is_streaming, message, message_timestamp, message_hash) 
                VALUES (?, ?, ?::jsonb, ?::timestamptz, MD5(?));
                """.formatted(livetimingTable);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ConsumerRecord<String, String> record : records) {
                LiveTimingMessage message = objectMapper.readValue(record.value(), LiveTimingMessage.class);

                if (message.message().isEmpty() || excludeCategories.contains(message.category())) {
                    Counter.builder("livetiming_storage_processor_record_discarded_total")
                            .description("Total number of live timing records discarded.")
                            .tag("category" , message.category())
                            .register(registry)
                            .increment();

                    LOG.debugf("Discarded >> offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
                    continue;
                }

                statement.setString(1, message.category());
                statement.setBoolean(2, message.isStreaming());
                statement.setString(3, message.message());
                statement.setString(4, message.timestamp().toString());
                statement.setString(5, message.message() + message.timestamp().toString());
                statement.addBatch();

                Counter.builder("livetiming_storage_processor_record_stored_total")
                        .description("Total number of live timing records written to storage.")
                        .tag("category" , message.category())
                        .register(registry)
                        .increment();

                // Submit batch in case we reach the batch size
                if (++recordCount % batchSize == 0) {
                    statement.executeBatch();
                    statement.clearBatch(); // Optional, but good practice
                }

                LOG.debugf("to Storage >> offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
                statusEmitter.send(record.value());
            }
            statement.executeBatch();

        } catch (Exception e) {
            LOG.warnf("Error when trying to store message. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }
}
