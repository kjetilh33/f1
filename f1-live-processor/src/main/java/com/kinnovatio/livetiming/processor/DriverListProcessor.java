package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.livetiming.model.SessionStateUpdate;
import com.kinnovatio.livetiming.repository.RepositoryUtilities;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
    private static final String driverListLiveKey = "driverListLive";
    private static final String driverListBaselineKey = "driverListBaseline";

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

    /// The timestamp from the most recent driver list message received.
    private final AtomicReference<Instant> driverListMessageTimestamp = new AtomicReference<>(Instant.now());

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

        if (message.isStreaming()) {
            // This is a live-streaming driver list update. Merge with the in-memory state.
            // The in-memory state will be written to storage by a separate scheduled task.
            JsonNode update = objectMapper.readTree(message.message());
            LOG.debugf("Received driver list message: %s", message.message());

            driverListRoot.updateAndGet(current -> {
                try {
                    // readerForUpdating modifies 'current' in-place or returns updated version
                    return objectMapper.readerForUpdating(current).readValue(update);
                } catch (IOException e) {
                    return current; // Fallback on error
                }
            });

            driverListUpdateTimestamp.set(Instant.now());
            driverListMessageTimestamp.set(message.timestamp().toInstant());
        } else {
            // This is an offline (non-live) update to the driver list. Will most likely contain the full
            // driver list state. Write directly to storage.
            storeBaselineDriverList(message);
        }
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
    public void storeLiveDriverList() {
        LOG.debugf("Running storeLiveDriverList task. Driver list update time: %s, storage time: %s",
                driverListUpdateTimestamp.get(), driverListStorageTimestamp.get());

        if (driverListUpdateTimestamp.get().isAfter(driverListStorageTimestamp.get())) {
            LOG.debugf("Updating driver list to storage: %s", driverListRoot.get().toString());

            try {
                repositoryUtilities.storeIntoKeyedMessageTable(
                        driverListTable,
                        driverListLiveKey,
                        stateManager.getSessionKey(),
                        objectMapper.writeValueAsString(driverListRoot.get()),
                        driverListMessageTimestamp.get());
            } catch (Exception e) {
                LOG.warnf("Error when trying to store driver list. Error: %s", e.getMessage());
            }

            driverListStorageTimestamp.set(Instant.now());
        }
    }

    /// Persists the baseline driver list to the database.
    ///
    /// This method is typically called for non-streaming messages that contain a complete,
    /// stable snapshot of the driver list, serving as a baseline for subsequent updates.
    ///
    /// @param message The baseline live timing message containing the driver list state.
    private void storeBaselineDriverList(LiveTimingMessage message) {
        LOG.debugf("Updating baseline driver list to storage: %s", driverListRoot.get().toString());

        try {
            repositoryUtilities.storeIntoKeyedMessageTable(
                    driverListTable,
                    driverListBaselineKey,
                    stateManager.getSessionKey(),
                    message.message(),
                    message.timestamp().toInstant());
        } catch (Exception e) {
            LOG.warnf("Error when trying to store driver list. Error: %s", e.getMessage());
        }
    }

    /// Responds to session state transitions by managing the driver list table.
    ///
    /// When a session ends (`NO_SESSION`), becomes `INACTIVE`, or a new `LIVE_SESSION` starts
    /// (excluding transitions from an inactive warmup), this method clears the existing
    /// driver list to ensure the dashboard or downstream consumers only see data
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
            LOG.infof("Session state changed from %s to %s. Will clear the live driver list from the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), driverListTable);
            int rowsAffected = repositoryUtilities.clearRowFromKeyedTable(driverListTable, driverListLiveKey);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, driverListTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), driverListTable);
        }
    }
}
