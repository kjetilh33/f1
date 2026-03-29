package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;

/// Processor for F1 Race Control messages.
///
/// This class consumes race control notifications (like flags, penalties, or safety car periods),
/// persists them to a relational database, and manages the lifecycle of the message table 
/// by clearing stale data when session states transition.
@ApplicationScoped
public class RaceControlMessageProcessor {
    private static final Logger LOG = Logger.getLogger(RaceControlMessageProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.race-control-message.table")
    String raceControlMessageTable;

    @Inject
    GlobalStateManager stateManager;

    /// Processes an incoming race control message and stores it in the database.
    ///
    /// @param recordValue The raw JSON string received from the "race-control-message" channel.
    /// @throws Exception If database connectivity fails or JSON parsing errors occur.
    @Incoming("race-control-message")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processRaceControlMessage(String recordValue) throws Exception {
        String sql = """
                INSERT INTO %s (session_id, message, message_timestamp)
                VALUES (?, ?::jsonb, ?::timestamptz)
                ;
                """.formatted(raceControlMessageTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        // Extract the race control message from the payload
        JsonNode root = objectMapper.readTree(message.message());
        List<String> raceControlMessages = new ArrayList<>();
        if (root.path("Messages").isObject() || root.path("Messages").isArray()) {
            root.path("Messages").elements().forEachRemaining(node -> {
                try {
                    raceControlMessages.add(objectMapper.writeValueAsString(node));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

        } else {
            LOG.warnf("Error when trying to parse race control message. Could not find message in payload: %s",
                    message.message());
            throw new JsonParseException("Error when trying to parse race control message.");
        }

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String raceControlMessage : raceControlMessages) {
                statement.setInt(1, stateManager.getSessionKey());
                statement.setString(2, raceControlMessage);
                statement.setString(3, message.timestamp().toString());
                statement.executeUpdate();
            }
        } catch (Exception e) {
            LOG.warnf("Error when trying to store race control message. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }

    /// Responds to session state transitions by managing the race control message table.
    ///
    /// When a session ends (`NO_SESSION`), becomes `INACTIVE`, or a new `LIVE_SESSION` starts 
    /// (excluding transitions from an inactive warmup), this method clears the existing 
    /// race control messages to ensure the dashboard or downstream consumers only see data 
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
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), raceControlMessageTable);
            int rowsAffected = repositoryUtilities.clearAllRowsFromTable(raceControlMessageTable);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, raceControlMessageTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), raceControlMessageTable);
        }
    }
}
