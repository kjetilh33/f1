package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
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
///
/// This processor handles track status updates (e.g., Yellow Flag, Safety Car, Green Flag)
/// by persisting them to a database. It also manages the lifecycle of the track status table
/// by clearing data when a session starts or ends.
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

    /// Processes an incoming track status message and stores it in the database.
    ///
    /// @param recordValue The raw JSON string received from the "track-status" channel.
    /// @throws Exception If database connectivity fails or JSON parsing errors occur.
    @Incoming("track-status")
    @Retry(delay = 500, maxRetries = 5)
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

    /// Listens for global session state changes and performs cleanup operations.
    ///
    /// If the session transitions to `NO_SESSION` or `LIVE_SESSION`, the track status table
    /// is cleared to prepare for a new session or clean up after one.
    ///
    /// @param sessionState The new state of the session.
    /// @throws Exception If the database delete operation fails.
    @Incoming("session-status-update")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionStatusChange(GlobalStateManager.SessionState sessionState) throws Exception {
        if (sessionState == GlobalStateManager.SessionState.NO_SESSION
                || sessionState == GlobalStateManager.SessionState.LIVE_SESSION) {
            LOG.infof("Session status changed to %s. Will clear the %s table.",
                    sessionState.getStatus(), trackStatusTable);
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
        } else {
            LOG.infof("Session status changed to %s. Will not clear the %s table.",
                    sessionState.getStatus(), trackStatusTable);
        }
    }
}
