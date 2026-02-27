package com.kinnovatio.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Set;

/// Processor for F1 track status messages.

@ApplicationScoped
public class F1TrackStatusProcessor {
    private static final Logger LOG = Logger.getLogger(F1TrackStatusProcessor.class);
    private static final String dbTableName = "live_timing_messages";

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

    /// Initializes the processor on startup.
    /// This method is triggered by the `StartupEvent`. It logs the startup configuration
    /// and ensures that the necessary database table exists.
    ///
    /// @param ev The startup event.
    public void onStartup(@Observes StartupEvent ev) {
        LOG.infof("Starting the live timing message storage processor...");

        createDbTableIfNotExists();

        LOG.infof("The storage processor is ready. Waiting for live timing messages...");
    }

    /// Creates the database table and indexes if they do not already exist.
    /// The table `live_timing_messages` stores the raw JSON message, timestamp, category,
    /// and a hash of the message content.
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
    /// @param record The record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("track-status")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processTrackStatus(Record<String, String> record) throws Exception {
        String sql = """
                INSERT INTO %s(category, is_streaming, message, message_timestamp, message_hash) VALUES(?, ?, ?::jsonb, ?::timestamptz, MD5(?));
                """.formatted(dbTableName);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            LiveTimingMessage message = objectMapper.readValue(record.value(), LiveTimingMessage.class);

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

            LOG.debugf("Track status to Storage >> key = %s, value = %s%n",  record.key(), record.value());

            statement.executeBatch();

        } catch (Exception e) {
            LOG.warnf("Error when trying to store message. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }
}
