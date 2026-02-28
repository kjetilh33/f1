package com.kinnovatio.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;

/// Processor for F1 track status messages.

@ApplicationScoped
public class F1SessionStatusProcessor {
    private static final Logger LOG = Logger.getLogger(F1SessionStatusProcessor.class);
    private static final String dbTableName = "live_timing_messages";


    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    MeterRegistry registry;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    @Broadcast
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("session-status-update")
    Emitter<String> sessionStatusUpdateEmitter;


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
    @Incoming("session-status")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionStatus(String recordValue) throws Exception {
        String sql = """
                INSERT INTO %s(category, is_streaming, message, message_timestamp, message_hash) VALUES(?, ?, ?::jsonb, ?::timestamptz, MD5(?));
                """.formatted(dbTableName);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

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

            //LOG.debugf("Track status to Storage >> key = %s, value = %s%n",  record.key(), record.value());

            //statement.executeBatch();

        } catch (Exception e) {
            LOG.warnf("Error when trying to store message. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }
}
