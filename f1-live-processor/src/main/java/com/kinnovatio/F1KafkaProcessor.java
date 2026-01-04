package com.kinnovatio;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@ApplicationScoped
public class F1KafkaProcessor {
    private static final Logger LOG = Logger.getLogger(F1KafkaProcessor.class);

    private final AtomicBoolean isDbHealthy = new AtomicBoolean();

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("status-out")
    Emitter<String> statusEmitter;

    public void onStartup(@Observes StartupEvent ev) {
        isDbHealthy.set(false);
        LOG.infof("Starting the live timing message storage processor...");

        isDbHealthy.set(createDbTableIfNotExists());
        if (!isDbHealthy.get()) {
            LOG.warn("The storage DB is not healthy. The processor will ignore messages and not store them.");
        }

        LOG.infof("The processor is ready. Waiting for live timing messages...");
    }

    private boolean createDbTableIfNotExists() {
        boolean isDbHealthy = false;
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS live_timing_messages (
                    message_id SERIAL PRIMARY KEY,
                    category VARCHAR(100) DEFAULT 'N/A',
                    message JSONB,
                    message_timestamp TIMESTAMPTZ,
                    created_timestamp TIMESTAMPTZ DEFAULT NOW()                    
                );
                """;

        String createIndexStatement = """
                CREATE INDEX IF NOT EXISTS idx_category_timestamp ON live_timing_messages (category, message_timestamp);
                """;

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            LOG.infof("Successfully connected to the storage DB...");

            statement.execute(createTableSql);
            LOG.infof("Successfully created (if not already exists) the DB table...");

            statement.execute(createIndexStatement);
            LOG.infof("Successfully created (if not already exists) the DB index...");

            isDbHealthy = true;
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s", e.getMessage());
            isDbHealthy = false;
        }

        return isDbHealthy;
    }

    @Incoming("f1-live-raw")
    @RunOnVirtualThread
    @Transactional
    public void toStorage(ConsumerRecords<String, String> records) {
        String sql = """
                INSERT INTO live_timing_messages(category, message, message_timestamp) VALUES(?, ?, ?);
                """;

        for (ConsumerRecord<String, String> record : records) {
            ZonedDateTime messageTimestamp = null;
            Headers headers = record.headers();
            Header timestampHeader = headers.lastHeader("timestamp");
            if (timestampHeader != null) {
                String timeStampString = new String(timestampHeader.value());
                try {
                    messageTimestamp = ZonedDateTime.parse(timeStampString);
                } catch (DateTimeParseException e) {
                    LOG.errorf("Unable to parse the message timestamp from message header: %s", timeStampString);
                }
            }

            System.out.printf(">> offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());

            statusEmitter.send(record.value());
        }

        return;
    }
}
