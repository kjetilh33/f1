package com.kinnovatio.livetiming.processor;

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

@ApplicationScoped
public class WeatherDataProcessor {
    private static final Logger LOG = Logger.getLogger(WeatherDataProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.weather-data.table")
    String weatherDataTable;

    @Inject
    GlobalStateManager stateManager;

    @Incoming("weather-data")
    @Retry(delay = 500, maxRetries = 5)
    @RunOnVirtualThread
    @Transactional
    public void processWeatherData(String recordValue) throws Exception {
        // Constant key used for the singleton row in the database table
        String weatherKey = "weatherData";

        String sql = """
                INSERT INTO %s (key, session_id, message, message_timestamp, updated_timestamp) 
                VALUES (?, ?, ?::jsonb, ?::timestamptz, NOW())
                ON CONFLICT (key)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    message_timestamp = EXCLUDED.message_timestamp,
                    updated_timestamp = EXCLUDED.updated_timestamp;
                """.formatted(weatherDataTable);

        LiveTimingMessage message = objectMapper.readValue(recordValue, LiveTimingMessage.class);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weatherKey);
            statement.setInt(2, stateManager.getSessionKey());
            statement.setString(3, message.message());
            statement.setString(4, message.timestamp().toString());
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store weather data. Will retry shortly. Error: %s", e.getMessage());
            throw e;
        }
    }

    /// Responds to session state transitions by managing the weather data table.
    ///
    /// When a session ends (`NO_SESSION`), becomes `INACTIVE`, or a new `LIVE_SESSION` starts
    /// (excluding transitions from an inactive warmup), this method clears the existing
    /// weather data to ensure the dashboard or downstream consumers only see data
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
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), weatherDataTable);
            int rowsAffected = repositoryUtilities.clearAllRowsFromTable(weatherDataTable);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, weatherDataTable);
        } else {
            LOG.infof("Session state changed from %s to %s. Will not clear the %s table.",
                    sessionStateUpdate.oldState().getStatus(), sessionStateUpdate.newState().getStatus(), weatherDataTable);
        }
    }
}
