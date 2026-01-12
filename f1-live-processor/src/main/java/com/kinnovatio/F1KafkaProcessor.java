package com.kinnovatio;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Set;

@ApplicationScoped
public class F1KafkaProcessor {
    private static final Logger LOG = Logger.getLogger(F1KafkaProcessor.class);
    private static final String dbTableName = "live_timing_messages";

    private static final Set<String> excludeCategories = Set.of("Heartbeat");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "log.source")
    String logSurce;

    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("status-out")
    Emitter<String> statusEmitter;

    public void onStartup(@Observes StartupEvent ev) {
        LOG.infof("Starting the live timing message storage processor...");
        LOG.infof("Config picked up from %s", logSurce);

        createDbTableIfNotExists();

        LOG.infof("The processor is ready. Waiting for live timing messages...");
    }

    private void createDbTableIfNotExists() {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    message_id SERIAL PRIMARY KEY,
                    category VARCHAR(100) DEFAULT 'N/A',
                    is_streaming BOOLEAN DEFAULT FALSE,
                    message JSONB,
                    message_timestamp TIMESTAMPTZ,
                    message_hash TEXT,
                    created_timestamp TIMESTAMPTZ DEFAULT NOW()
                );
                """.formatted(dbTableName);

        String createIndexStatement = """
                CREATE INDEX IF NOT EXISTS idx_category_timestamp ON %s (category, message_timestamp);
                CREATE INDEX IF NOT EXISTS idx_hash ON %s (message_hash);
                """.formatted(dbTableName, dbTableName);

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            LOG.infof("Successfully connected to the storage DB...");

            statement.execute(createTableSql);
            LOG.infof("Successfully created (if not already exists) the DB table...");

            statement.execute(createIndexStatement);
            LOG.infof("Successfully created (if not already exists) the DB index...");
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s", e.getMessage());
        }
    }

    @Incoming("f1-live-raw")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void toStorage(ConsumerRecords<String, String> records) throws Exception {
        final int batchSize = 1000;
        int recordCount = 0;

        String sql = """
                INSERT INTO %s(category, is_streaming, message, message_timestamp, message_hash) VALUES(?, ?, ?::jsonb, ?::timestamptz, MD5(?));
                """.formatted(dbTableName);

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
