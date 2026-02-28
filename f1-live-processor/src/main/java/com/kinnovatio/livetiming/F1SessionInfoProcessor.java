package com.kinnovatio.livetiming;

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
    Emitter<String> sessionStatusUpdateEmitter;


    @Incoming("session-info")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processSessionStatus(String recordValue) throws Exception {
        String sessionStatusKey = "sessionStatus";

        String sql = """
                INSERT INTO %s (key, message) 
                VALUES (?, ?::jsonb)
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message;
                """.formatted(sessionInfoTable);

        try (Connection connection = storageDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

            statement.setString(1, sessionStatusKey);
            statement.setString(2, message.message());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store session status. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }

        // Check for updated session status

    }
}
