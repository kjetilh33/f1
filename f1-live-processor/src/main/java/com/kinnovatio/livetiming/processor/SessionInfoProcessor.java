package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.livetiming.model.SessionStatus;
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
import java.time.ZoneId;

/// Processor for F1 session status messages.
///
/// This processor listens to the "session-info" channel, persists the latest state to a Postgres database,
/// parses the payload to determine the current session lifecycle state (Live, Inactive, etc.),
/// and broadcasts updates to the rest of the application.
@ApplicationScoped
public class SessionInfoProcessor {
    private static final Logger LOG = Logger.getLogger(SessionInfoProcessor.class);
    /// Default fallback value used when specific JSON fields are missing or null.
    private static final String defaultStatus = "unknown";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.session-info.table")
    String sessionInfoTable;

    @Inject
    GlobalStateManager stateManager;

    /// Emitter for broadcasting significant changes in session state (e.g., from LIVE_SESSION to NO_SESSION).
    /// Uses DROP strategy to handle backpressure if downstream consumers are slow.
    @Inject
    @Broadcast
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("session-status-update")
    Emitter<GlobalStateManager.SessionState> sessionStatusUpdateEmitter;


    /// Processes an incoming raw JSON record containing session information.
    ///
    /// This method performs the following steps:
    /// 1. Persists the raw message to the database (Upsert).
    /// 2. Parses the internal JSON message to extract session details.
    /// 3. Determines the high-level state (Live, Finalised, etc.), utilizing a fallback mechanism via 'ArchiveStatus'.
    /// 4. Updates the global state manager.
    /// 5. Emits a notification if the state has changed.
    ///
    /// @param recordValue The raw JSON string received from the message broker.
    /// @throws Exception If database connectivity fails or JSON parsing errors occur.
    @Incoming("session-info")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionInfo(String recordValue) throws Exception {
        // Constant key used for the singleton row in the database table
        String sessionInfoKey = "sessionInfo";

        String upsertSessionInfoSql = """
                INSERT INTO %s (key, message, message_timestamp, updated_timestamp) 
                VALUES (?, ?::jsonb, ?::timestamptz, NOW())
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    message_timestamp = EXCLUDED.message_timestamp,
                    updated_timestamp = EXCLUDED.updated_timestamp;
                """.formatted(sessionInfoTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(upsertSessionInfoSql)) {
            LOG.infof("Storing session info. Using timstamp strng: %s", message.timestamp().toString());
            statement.setString(1, sessionInfoKey);
            statement.setString(2, message.message());
            statement.setString(3, message.timestamp().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session info. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }

        // Check for updated session status
        JsonNode root = objectMapper.readTree(message.message());
        String sessionStatus = root.path("SessionStatus").asText(defaultStatus);
        String archiveStatus = root.path("ArchiveStatus").path("Status").asText(defaultStatus);
        String meetingName = root.path("Meeting").path("Name").asText(defaultStatus);
        String sessionName = root.path("Name").asText(defaultStatus);
        int sessionKey = root.path("Key").asInt(-1);

        stateManager.setSessionKey(sessionKey);
        LOG.infof("Received session information about %s, %s, with session id %d, status %s.",
                meetingName, sessionName, sessionKey, sessionStatus);

        // If `SessionStatus` is not populated, fall back on `ArchiveStatus` as the signal
        if (sessionStatus.equals(defaultStatus)) {
            sessionStatus = switch (archiveStatus) {
                case "Generating" -> "Started";
                case "Complete" -> "Finalised";
                default -> defaultStatus;
            };
            LOG.infof("No session status in the SessionInfo payload. Using ArchiveStatus to determine session status. "
                    + "Archive status: %s. Estimated session status: %s",
                    archiveStatus, sessionStatus);
        }

        // Map the string status to the internal enum representation
        GlobalStateManager.SessionState newSessionState = switch (sessionStatus) {
            case "Started" -> GlobalStateManager.SessionState.LIVE_SESSION;
            case "Finalised" -> GlobalStateManager.SessionState.NO_SESSION;
            case "Inactive" -> GlobalStateManager.SessionState.INACTIVE;
            default -> GlobalStateManager.SessionState.UNKNOWN;
        };

        processSessionStateChange(newSessionState);
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
        // Constant key used for the singleton row in the database table
        String sessionStateKey = "sessionState";

        String upsertSessionStateSql = """
                INSERT INTO %s (key, message, message_timestamp, updated_timestamp) 
                VALUES (?, ?::jsonb, ?::timestamptz, NOW())
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    message_timestamp = EXCLUDED.message_timestamp,
                    updated_timestamp = EXCLUDED.updated_timestamp;
                """.formatted(sessionInfoTable);

        SessionStatus sessionStatus = new SessionStatus(sessionState.getStatusValue(), sessionState.getStatus());

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSessionStateSql)) {
            LOG.infof("Storing session state. Using timstamp strng: %s", Instant.now().atZone(ZoneId.of("UTC")).toString());
            statement.setString(1, sessionStateKey);
            statement.setString(2, objectMapper.writeValueAsString(sessionStatus));
            statement.setString(3, Instant.now().atZone(ZoneId.of("UTC")).toString());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session state. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }

    /// Periodically checks if the input data stream is still active during a live session.
    ///
    /// This scheduled task runs every 2 minutes. It verifies the time elapsed since the last
    /// received message. If the session is currently marked as {@code LIVE_SESSION} but no
    /// messages have been received for over 30 minutes, the session state is forcibly transitioned
    /// to {@code UNKNOWN} to reflect the potential loss of connectivity or stream termination.
    @RunOnVirtualThread
    @Scheduled(every = "2m", delayed = "30m")
    public void checkInputDataStreamAlive() {
        Duration limit = Duration.ofMinutes(30);
        Duration elapsed = Duration.between(stateManager.getLastMessageReceived(), Instant.now());
        if (stateManager.getSessionState() == GlobalStateManager.SessionState.LIVE_SESSION && elapsed.compareTo(limit) > 0) {
            LOG.warnf("We are in a live session, but have not received any messages for 30 minutes. "
                    + "Will set the session state to UNKNOWN.");
            processSessionStateChange(GlobalStateManager.SessionState.UNKNOWN);
        }
    }

    /// Updates the global session state and broadcasts changes if a transition occurs.
    ///
    /// This method compares the proposed new state against the current state in {@link GlobalStateManager}.
    /// If they differ, it updates the manager, emits the new state to the "session-status-update" channel,
    /// and logs the event.
    ///
    /// @param newSessionState The new session state to apply.
    private void processSessionStateChange(GlobalStateManager.SessionState newSessionState) {
        // Detect state transitions and notify subscribers
        if (stateManager.getSessionState() != newSessionState) {
            stateManager.setSessionState(newSessionState);
            sessionStatusUpdateEmitter.send(newSessionState);
            LOG.infof("We have an update session status. New session status: %s.",
                    newSessionState);
        }
    }
}
