package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RaceControlMessageProcessor {
    private static final Logger LOG = Logger.getLogger(RaceControlMessageProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.race-control-message.table")
    String raceControlMessageTable;

    @Inject
    GlobalStateManager stateManager;

    /// Processes an incoming track status message and stores it in the database.
    ///
    /// @param recordValue The raw JSON string received from the "track-status" channel.
    /// @throws Exception If database connectivity fails or JSON parsing errors occur.
    @Incoming("race-control-message")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processRaceControlMessage(String recordValue) throws Exception {
        String sql = """
                INSERT INTO %s (session_key, message, message_timestamp)
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
                    sessionState.getStatus(), raceControlMessageTable);
            String sql = """
                    DELETE FROM %s;
                    """.formatted(raceControlMessageTable);

            try (Connection connection = storageDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            } catch (Exception e) {
                LOG.warnf("Error when trying to clear the %s table. Will retry shortly. Error: %s",
                        raceControlMessageTable,
                        e.getMessage());
                throw e;
            }
        } else {
            LOG.infof("Session status changed to %s. Will not clear the %s table.",
                    sessionState.getStatus(), raceControlMessageTable);
        }
    }
}
