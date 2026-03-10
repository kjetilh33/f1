package com.kinnovatio.livetiming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
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

/// Processor for F1 session status messages.
@ApplicationScoped
public class F1SessionInfoProcessor {
    private static final Logger LOG = Logger.getLogger(F1SessionInfoProcessor.class);
    private static final String defaultStatus = "unknown";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.session-info.table")
    String sessionInfoTable;

    @Inject
    MeterRegistry registry;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    @Broadcast
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("session-status-update")
    Emitter<GlobalStateManager.SessionState> sessionStatusUpdateEmitter;


    @Incoming("session-info")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionInfo(String recordValue) throws Exception {
        String sessionStatusKey = "sessionInfo";

        String sql = """
                INSERT INTO %s (key, message, message_timestamp) 
                VALUES (?, ?::jsonb, ?::timestamptz)
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    message_timestamp = EXCLUDED.message_timestamp;
                """.formatted(sessionInfoTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionStatusKey);
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
        int sessionKey = root.path("Key").asInt(-1);
        LOG.infof("We have an update session status. New session status: %s. Archive status: %s",
                sessionStatus, archiveStatus);

        stateManager.setSessionKey(sessionKey);

        if (sessionStatus.equalsIgnoreCase("Started")) {
            stateManager.setSessionState(GlobalStateManager.SessionState.LIVE_SESSION);
            sessionStatusUpdateEmitter.send(GlobalStateManager.SessionState.LIVE_SESSION);
        } else if (sessionStatus.equalsIgnoreCase("Finalised")) {
            stateManager.setSessionState(GlobalStateManager.SessionState.NO_SESSION);
            sessionStatusUpdateEmitter.send(GlobalStateManager.SessionState.NO_SESSION);
        } else if (sessionStatus.equalsIgnoreCase("Inactive")) {
            stateManager.setSessionState(GlobalStateManager.SessionState.INACTIVE);
            sessionStatusUpdateEmitter.send(GlobalStateManager.SessionState.INACTIVE);
        }else {
            stateManager.setSessionState(GlobalStateManager.SessionState.UNKNOWN);
            sessionStatusUpdateEmitter.send(GlobalStateManager.SessionState.UNKNOWN);
        }
    }
}
