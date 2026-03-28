package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.livetiming.model.SessionStatus;
import com.kinnovatio.livetiming.repository.RepositoryUtilities;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/// Processor for F1 driver list messages.

@ApplicationScoped
public class DriverListProcessor {
    private static final Logger LOG = Logger.getLogger(DriverListProcessor.class);
    /// Default fallback value used when specific JSON fields are missing or null.
    private static final String defaultStatus = "unknown";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.driver-list.table")
    String driverListTable;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    RepositoryUtilities repositoryUtilities;

    private final AtomicReference<JsonNode> driverListRoot = new AtomicReference<>(objectMapper.createObjectNode());

    @Incoming("driver-list")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processDriverList(String recordValue) throws Exception {
        // Constant key used for the singleton row in the database table
        String driverListKey = "driverList";

        String upsertSessionInfoSql = """
                INSERT INTO %s (key, message, message_timestamp, updated_timestamp) 
                VALUES (?, ?::jsonb, ?::timestamptz, NOW())
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    message_timestamp = EXCLUDED.message_timestamp,
                    updated_timestamp = EXCLUDED.updated_timestamp;
                """.formatted(driverListTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(upsertSessionInfoSql)) {
            statement.setString(1, driverListKey);
            statement.setString(2, message.message());
            statement.setString(3, message.timestamp().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session info. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }

    @RunOnVirtualThread
    @Scheduled(every = "5s", delayed = "5s")
    public void storeDriverList() {
        Duration limit = Duration.ofMinutes(30);
        Duration elapsed = Duration.between(stateManager.getLastMessageReceived(), Instant.now());
        if (stateManager.getSessionState() == GlobalStateManager.SessionState.LIVE_SESSION && elapsed.compareTo(limit) > 0) {
            LOG.warnf("We are in a live session, but have not received any messages for 30 minutes. "
                    + "Will set the session state to UNKNOWN.");
        }
    }

    /// Listens for global session state changes and performs cleanup operations.
    ///
    /// If the session transitions to `NO_SESSION` or `LIVE_SESSION`, the race message table
    /// is cleared to prepare for a new session or clean up after one.
    ///
    /// @param sessionState The new state of the session.
    /// @throws Exception If the database delete operation fails.
    @Incoming("session-status-update")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    public void processSessionStatusChange(GlobalStateManager.SessionState sessionState) throws Exception {
        if (sessionState == GlobalStateManager.SessionState.NO_SESSION
                || sessionState == GlobalStateManager.SessionState.LIVE_SESSION) {
            LOG.infof("Session status changed to %s. Will clear the %s table.",
                    sessionState.getStatus(), driverListTable);
            int rowsAffected = repositoryUtilities.clearAllRowsFromTable(driverListTable);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, driverListTable);
        } else {
            LOG.infof("Session status changed to %s. Will not clear the %s table.",
                    sessionState.getStatus(), driverListTable);
        }
    }
}
