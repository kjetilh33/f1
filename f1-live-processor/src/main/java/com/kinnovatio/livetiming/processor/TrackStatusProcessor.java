package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.livetiming.model.SessionStateUpdate;
import com.kinnovatio.livetiming.repository.RepositoryUtilities;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
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

/// Processor for F1 track status messages.
///
/// This processor handles track status updates (e.g., Yellow Flag, Safety Car, Green Flag)
/// by persisting them to a database. It also manages the lifecycle of the track status table
/// by clearing data when a session starts or ends.
@ApplicationScoped
public class TrackStatusProcessor {
    private static final Logger LOG = Logger.getLogger(TrackStatusProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

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
                INSERT INTO %s (session_id, message, message_timestamp)
                VALUES (?, ?::jsonb, ?::timestamptz)
                ;
                """.formatted(trackStatusTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);
        LOG.infof("TrackStatusProcessor: Received track status message: %s", message.message());

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

    /// Responds to session state transitions by managing the track status table.
    ///
    /// When a session ends (`NO_SESSION`), becomes `INACTIVE`, or a new `LIVE_SESSION` starts
    /// (excluding transitions from an inactive warmup), this method clears the existing
    /// track status messages to ensure the dashboard or downstream consumers only see data
    /// relevant to the current active session.
    ///
    /// @param sessionStateUpdate The transition details between the old and new session states.
    /// @throws Exception If the database cleanup operation fails.
    @Incoming("session-status-update")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    public void processSessionStatusChange(SessionStateUpdate sessionStateUpdate) throws Exception {
        if (sessionStateUpdate.newState() == GlobalStateManager.SessionState.NO_SESSION
                || sessionStateUpdate.newState() == GlobalStateManager.SessionState.INACTIVE
                || (sessionStateUpdate.newState() == GlobalStateManager.SessionState.LIVE_SESSION
                && sessionStateUpdate.oldState() != GlobalStateManager.SessionState.INACTIVE)) {
            LOG.infof("Session state changed from %s to %s. Will clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), trackStatusTable);
            int rowsAffected = repositoryUtilities.clearAllRowsFromTable(trackStatusTable);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, trackStatusTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), trackStatusTable);
        }
    }
}
