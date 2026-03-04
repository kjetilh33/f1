package com.kinnovatio.livetiming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

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

    @ConfigProperty(name = "app.track-status.table")
    String trackStatusTable;

    @Inject
    GlobalStateManager stateManager;

    /// Processes a batch of Kafka records and stores them in the database.
    /// @param record The record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("track-status")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processTrackStatus(String recordValue) throws Exception {
        String sql = """
                INSERT INTO %s (session_key, message, message_timestamp)
                VALUES (?, ?::jsonb, ?::timestamptz)
                ;
                """.formatted(trackStatusTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, stateManager.getSessionKey());
            statement.setString(2, message.message());
            statement.setString(3, message.timestamp().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store track status. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }

    @Incoming("session-status-update")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionStatusChange(GlobalStateManager.SessionState sessionState) throws Exception {
        LOG.infof("Session status changed. Will clear the %s table.", trackStatusTable);
        String sql = """
                DELETE FROM %s;
                """.formatted(trackStatusTable);

        try (Connection connection = storageDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception e) {
            LOG.warnf("Error when trying to clear the %s table. Will retry shortly. Error: %s",
                    trackStatusTable,
                    e.getMessage());
            throw e;
        }
    }
}
