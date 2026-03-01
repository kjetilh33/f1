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


    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    MeterRegistry registry;


    /// Processes a batch of Kafka records and stores them in the database.
    /// @param record The record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("track-status")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processTrackStatus(String recordValue) throws Exception {
        String sessionStatusKey = "trackStatus";
/*
        String sql = """
                INSERT INTO %s (key, message)
                VALUES (?, ?::jsonb)
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message;
                """.formatted(sessionInfoTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionStatusKey);
            statement.setString(2, message.message());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session status. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }

 */
    }

    @Incoming("session-status-update")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionStatusChange(GlobalStateManager.SessionState sessionState) throws Exception {
        String sessionStatusKey = "trackStatus";
/*
        String sql = """
                INSERT INTO %s (key, message)
                VALUES (?, ?::jsonb)
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message;
                """.formatted(sessionInfoTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionStatusKey);
            statement.setString(2, message.message());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session status. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }

 */
    }
}
