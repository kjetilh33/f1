package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/// Processor for F1 timing app data messages.
///
/// This component aggregates partial JSON updates into a complete timing data state
/// and periodically persists that state to the database. It also handles cleanup
/// when the session status changes.
@ApplicationScoped
public class TimingAppDataProcessor {
    private static final Logger LOG = Logger.getLogger(TimingAppDataProcessor.class);
    private static final String timingAppDataLiveKey = "timingAppDataLive";
    private static final String timingAppDataBaselineKey = "timingAppDataBaseline";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    /// The database table name where timing app data is stored, sourced from configuration.
    @ConfigProperty(name = "app.timing-app-data.table")
    String timingAppDataTable;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    RepositoryUtilities repositoryUtilities;

    /// Holds the current consolidated state of the timing data as a JSON tree.
    /// Updated in-place by incoming message updates.
    private final AtomicReference<JsonNode> dataRoot = new AtomicReference<>();

    /// The timestamp from the most recent timing data message received.
    private final AtomicReference<Instant> timingAppDataMessageTimestamp = new AtomicReference<>(Instant.now());

    /// The timestamp of the most recent update received from the live timing stream.
    private final AtomicReference<Instant> timingAppDataUpdateTimestamp = new AtomicReference<>(Instant.now());

    /// The timestamp of the last successful database persistence operation.
    /// Used to determine if a new write is necessary.
    private final AtomicReference<Instant> timingAppDataStorageTimestamp = new AtomicReference<>(Instant.now());

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
    @Incoming("timing-app-data")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    public void processTimingData(String recordValue) throws Exception {
        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        // Convert array notation to object notation
        message = processMessage(message);

        if (message.isStreaming()) {
            // This is a live-streaming timing app data update. Merge with the in-memory state.
            // The in-memory state will be written to storage by a separate scheduled task.
            JsonNode update = objectMapper.readTree(message.message());
            LOG.debugf("Received timing app data message: %s", message.message());

            dataRoot.updateAndGet(current -> {
                try {
                    // readerForUpdating modifies 'current' in-place or returns updated version
                    return objectMapper.readerForUpdating(current).readValue(update);
                } catch (IOException e) {
                    return current; // Fallback on error
                }
            });

            timingAppDataUpdateTimestamp.set(Instant.now());
            timingAppDataMessageTimestamp.set(message.timestamp());
        } else {
            // This is an offline (non-live) update to the timing app data. Check if it is a valid init message.
            // There should always be a driver with nr "1". Probe this first.
            JsonNode root = objectMapper.readTree(message.message());
            JsonNode driver1 = root.path("lines").path("1");
            if (driver1.isObject() && driver1.path("gridPos").isValueNode()
                    && driver1.path("line").isValueNode()) {
                LOG.infof("Received a valid baseline timing app data message. Will use this as a new baseline.");
                storeBaselineTimingAppData(message);
            } else {
                LOG.infof("TimingAppDataProcessor: Received non-streaming message. Message did not validate as a baseline. "
                        + "Message excerpt: %s",message.message().substring(0, Math.min(200, message.message().length() - 1)));
            }
        }
    }

    /// Check the Json message for stints in array notation and convert them to object notation.
    private LiveTimingMessage processMessage(LiveTimingMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.message());
            if (root.path("lines").isObject()) {
                Set<Map.Entry<String, JsonNode>> lines = root.path("lines").properties();
                for (Map.Entry<String, JsonNode> line : lines) {
                    if (line.getValue().path("stints").isArray()) {
                        // We have a sectors array. Convert it and its content to object notation
                        ObjectNode lineObject = (ObjectNode) line.getValue();
                        ArrayNode stintsArray = (ArrayNode) lineObject.path("stints");
                        ObjectNode stints = objectMapper.createObjectNode();
                        int counter = 0;
                        for (JsonNode stint : stintsArray) {
                            stints.set(String.valueOf(counter), stint);
                            counter++;
                        }
                        lineObject.set("stints", stints);
                    }
                }
            } else {
                LOG.warnf("Could not find the expected _lines_ property in the Json payload: %s",
                        message.message().substring(0, Math.min(200, message.message().length() - 1)));
            }

            return new LiveTimingMessage(message.category(),
                    objectMapper.writeValueAsString(root),
                    message.timestamp(),
                    message.isStreaming());

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /// Periodically persists the current timing data state to the database.
    ///
    /// The operation is only performed if [timingAppDataUpdateTimestamp] is newer than
    /// [timingAppDataStorageTimestamp], indicating there is unsaved data.
    @RunOnVirtualThread
    @Scheduled(every = "5s", delayed = "5s")
    @Transactional
    public void storeLiveTimingData() {
        LOG.debugf("Running storeLiveTimingData task. Timing data update time: %s, storage time: %s",
                timingAppDataUpdateTimestamp.get(), timingAppDataStorageTimestamp.get());

        if (timingAppDataUpdateTimestamp.get().isAfter(timingAppDataStorageTimestamp.get())) {
            LOG.debugf("Updating timing app data to storage: %s", dataRoot.get().toString());

            try {
                repositoryUtilities.storeIntoKeyedMessageTable(
                        timingAppDataTable,
                        timingAppDataLiveKey,
                        stateManager.getSessionKey(),
                        objectMapper.writeValueAsString(dataRoot.get()),
                        timingAppDataMessageTimestamp.get());
            } catch (Exception e) {
                LOG.warnf("Error when trying to store timing app data. Error: %s", e.getMessage());
            }

            timingAppDataStorageTimestamp.set(Instant.now());
        }
    }

    /// Persists the baseline timing app data to the database.
    ///
    /// This method is typically called for non-streaming messages that contain a complete,
    /// stable snapshot of the timing data, serving as a baseline for subsequent updates.
    ///
    /// @param message The baseline live timing message containing the timing data state.
    private void storeBaselineTimingAppData(LiveTimingMessage message) {
        LOG.debugf("Updating baseline timing app data to storage: %s", dataRoot.get().toString());

        try {
            repositoryUtilities.storeIntoKeyedMessageTable(
                    timingAppDataTable,
                    timingAppDataBaselineKey,
                    stateManager.getSessionKey(),
                    message.message(),
                    message.timestamp());
        } catch (Exception e) {
            LOG.warnf("Error when trying to store timing app data. Error: %s", e.getMessage());
        }
    }

    /// Initialize the timing data to an empty Json object node.
    private void initializeDataRoot() {
        dataRoot.set(objectMapper.createObjectNode());
    }

    /// Responds to session state transitions by managing the timing app data table.
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
            LOG.infof("Session state changed from %s to %s. Will clear the live timing app data from the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), timingAppDataTable);
            initializeDataRoot();
            int rowsAffected = repositoryUtilities.clearRowFromKeyedTable(timingAppDataTable, timingAppDataLiveKey);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, timingAppDataTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), timingAppDataTable);
        }
    }
}
