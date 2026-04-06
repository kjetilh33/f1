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
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/// Processor for F1 driver list messages.
///
/// This component aggregates partial JSON updates into a complete timing data state
/// and periodically persists that state to the database. It also handles cleanup
/// when the session status changes.
@ApplicationScoped
public class TimingDataProcessor {
    private static final Logger LOG = Logger.getLogger(TimingDataProcessor.class);
    private static final String timingDataLiveKey = "timingDataLive";
    private static final String timingDataBaselineKey = "timingDataBaseline";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    /// The database table name where timing data data is stored, sourced from configuration.
    @ConfigProperty(name = "app.timing-data.table")
    String timingDataTable;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    RepositoryUtilities repositoryUtilities;

    /// Holds the current consolidated state of the timing data as a JSON tree.
    /// Updated in-place by incoming message updates.
    private final AtomicReference<JsonNode> dataRoot = new AtomicReference<>();

    /// The timestamp from the most recent timing data message received.
    private final AtomicReference<Instant> timingDataMessageTimestamp = new AtomicReference<>(Instant.now());

    /// The timestamp of the most recent update received from the live timing stream.
    private final AtomicReference<Instant> timingDataUpdateTimestamp = new AtomicReference<>(Instant.now());

    /// The timestamp of the last successful database persistence operation.
    /// Used to determine if a new write is necessary.
    private final AtomicReference<Instant> timingDataStorageTimestamp = new AtomicReference<>(Instant.now());

    // This runs AFTER 'objectMapper' is injected
    @PostConstruct
    void init() {
        initializeDataRoot();
    }

    /// Processes incoming timing data updates from the message broker.
    ///
    /// This method uses Jackson's `readerForUpdating` to merge incoming partial updates
    /// into the existing state stored in [dataRoot].
    ///
    /// @param recordValue The raw JSON message containing timing data updates.
    /// @throws Exception if message parsing fails.
    @Incoming("timing-data")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    public void processTimingData(String recordValue) throws Exception {
        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        if (message.isStreaming()) {
            // This is a live-streaming timing data update. Merge with the in-memory state.
            // The in-memory state will be written to storage by a separate scheduled task.
            JsonNode update = objectMapper.readTree(message.message());
            LOG.debugf("Received timing data message: %s", message.message());

            dataRoot.updateAndGet(current -> {
                try {
                    // readerForUpdating modifies 'current' in-place or returns updated version
                    return objectMapper.readerForUpdating(current).readValue(update);
                } catch (IOException e) {
                    return current; // Fallback on error
                }
            });

            timingDataUpdateTimestamp.set(Instant.now());
            timingDataMessageTimestamp.set(message.timestamp().toInstant());
        } else {
            // This is an offline (non-live) update to the timing data. Check if it is a valid init message.
            // There should always be a driver with nr "1". Probe this first.
            JsonNode root = objectMapper.readTree(message.message());
            JsonNode driver1 = root.path("Lines").path("1");
            if (driver1.isObject() && driver1.path("BestLapTime").isObject()
                    && driver1.path("BestLapTime").path("Value").asText("").isBlank()) {
                LOG.infof("Received a valid baseline timing data message. Will use this as a new baseline.");
                storeBaselineTimingData(message);
            } else {
                LOG.infof("TimingDataProcessor: Received non-streaming message. Message did not validate as a baseline. "
                        + "Message excerpt: %s",message.message().substring(0, Math.min(200, message.message().length() - 1)));
            }
        }
    }

    /// Periodically persists the current timing data state to the database.
    ///
    /// The operation is only performed if [timingDataUpdateTimestamp] is newer than
    /// [timingDataStorageTimestamp], indicating there is unsaved data.
    ///
    /// An `UPSERT` (INSERT ... ON CONFLICT) strategy is used to maintain a single record
    /// per session/key.
    @RunOnVirtualThread
    @Scheduled(every = "5s", delayed = "5s")
    @Transactional
    public void storeLiveTimingData() {
        LOG.debugf("Running storeLiveTimingData task. Timing data update time: %s, storage time: %s",
                timingDataUpdateTimestamp.get(), timingDataStorageTimestamp.get());

        if (timingDataUpdateTimestamp.get().isAfter(timingDataStorageTimestamp.get())) {
            LOG.debugf("Updating timing data to storage: %s", dataRoot.get().toString());

            try {
                repositoryUtilities.storeIntoKeyedMessageTable(
                        timingDataTable,
                        timingDataLiveKey,
                        stateManager.getSessionKey(),
                        objectMapper.writeValueAsString(dataRoot.get()),
                        timingDataMessageTimestamp.get());
            } catch (Exception e) {
                LOG.warnf("Error when trying to store timing data. Error: %s", e.getMessage());
            }

            timingDataStorageTimestamp.set(Instant.now());
        }
    }

    /// Persists the baseline timing data to the database.
    ///
    /// This method is typically called for non-streaming messages that contain a complete,
    /// stable snapshot of the timing data, serving as a baseline for subsequent updates.
    ///
    /// @param message The baseline live timing message containing the timing data state.
    private void storeBaselineTimingData(LiveTimingMessage message) {
        LOG.debugf("Updating baseline timing data to storage: %s", dataRoot.get().toString());

        try {
            repositoryUtilities.storeIntoKeyedMessageTable(
                    timingDataTable,
                    timingDataBaselineKey,
                    stateManager.getSessionKey(),
                    message.message(),
                    message.timestamp().toInstant());
        } catch (Exception e) {
            LOG.warnf("Error when trying to store timing data. Error: %s", e.getMessage());
        }
    }

    /// Initialize the timing data to an empty Json object node.
    private void initializeDataRoot() {
        dataRoot.set(objectMapper.createObjectNode());
    }

    /// Responds to session state transitions by managing the timing data table.
    ///
    /// When a session ends (`NO_SESSION`), becomes `INACTIVE`, or a new `LIVE_SESSION` starts
    /// (excluding transitions from an inactive warmup), this method clears the existing
    /// timing data to ensure the dashboard or downstream consumers only see data
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
            LOG.infof("Session state changed from %s to %s. Will clear the live timing data from the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), timingDataTable);
            initializeDataRoot();
            int rowsAffected = repositoryUtilities.clearRowFromKeyedTable(timingDataTable, timingDataLiveKey);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, timingDataTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), timingDataTable);
        }
    }
}
