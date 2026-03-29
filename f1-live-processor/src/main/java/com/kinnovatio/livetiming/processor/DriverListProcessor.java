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
import jakarta.annotation.PostConstruct;
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/// Processor for F1 driver list messages.
///
/// This component aggregates partial JSON updates into a complete driver list state
/// and periodically persists that state to the database. It also handles cleanup
/// when the session status changes.
@ApplicationScoped
public class DriverListProcessor {
    private static final Logger LOG = Logger.getLogger(DriverListProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    /// The database table name where driver list data is stored, sourced from configuration.
    @ConfigProperty(name = "app.driver-list.table")
    String driverListTable;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    RepositoryUtilities repositoryUtilities;

    /// Holds the current consolidated state of the driver list as a JSON tree.
    /// Updated in-place by incoming message updates.
    private final AtomicReference<JsonNode> driverListRoot = new AtomicReference<>();

    /// The timestamp of the most recent update received from the live timing stream.
    private final AtomicReference<Instant> driverListUpdateTimestamp = new AtomicReference<>(Instant.now());

    /// The timestamp of the last successful database persistence operation.
    /// Used to determine if a new write is necessary.
    private final AtomicReference<Instant> driverListStorageTimestamp = new AtomicReference<>(Instant.now());

    // 2. This runs AFTER 'mapper' is injected
    @PostConstruct
    void init() {
        driverListRoot.set(objectMapper.createObjectNode());
    }

    /// Processes incoming driver list updates from the message broker.
    ///
    /// This method uses Jackson's `readerForUpdating` to merge incoming partial updates
    /// into the existing state stored in [driverListRoot].
    ///
    /// @param recordValue The raw JSON message containing driver list updates.
    /// @throws Exception if message parsing fails.
    @Incoming("driver-list")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    public void processDriverList(String recordValue) throws Exception {
        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);
        JsonNode update = objectMapper.readTree(message.message());
        LOG.infof("Received driver list message: %s", message.message());
        
        driverListRoot.updateAndGet(current -> {
            try {
                // readerForUpdating modifies 'current' in-place or returns updated version
                return objectMapper.readerForUpdating(current).readValue(update);
            } catch (IOException e) {
                return current; // Fallback on error
            }
        });
        
        driverListUpdateTimestamp.set(message.timestamp().toInstant());
    }

    /// Periodically persists the current driver list state to the database.
    ///
    /// The operation is only performed if [driverListUpdateTimestamp] is newer than
    /// [driverListStorageTimestamp], indicating there is unsaved data.
    ///
    /// An `UPSERT` (INSERT ... ON CONFLICT) strategy is used to maintain a single record
    /// per session/key.
    @RunOnVirtualThread
    @Scheduled(every = "5s", delayed = "5s")
    @Transactional
    public void storeDriverList() {
        if (driverListUpdateTimestamp.get().isAfter(driverListStorageTimestamp.get())) {
            LOG.infof("Updating driver list to storage: %s", driverListRoot.get().toString());
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

            try (Connection connection = storageDataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsertSessionInfoSql)) {
                statement.setString(1, driverListKey);
                statement.setString(2, objectMapper.writeValueAsString(driverListRoot.get()));
                statement.setObject(3, OffsetDateTime.ofInstant(driverListUpdateTimestamp.get(), ZoneOffset.UTC));
                statement.executeUpdate();
                driverListStorageTimestamp.set(Instant.now());
            } catch (Exception e) {
                LOG.warnf("Error when trying to store driver list. Error: %s", e.getMessage());
            }
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
