package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
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
                INSERT INTO %s (key, session_key, message, message_timestamp, updated_timestamp) 
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
                    sessionState.getStatus(), weatherDataTable);
            int rowsAffected = repositoryUtilities.clearAllRowsFromTable(weatherDataTable);
            LOG.infof("%d rows deleted from the %s table.", rowsAffected, weatherDataTable);
        } else {
            LOG.infof("Session status changed to %s. Will not clear the %s table.",
                    sessionState.getStatus(), weatherDataTable);
        }
    }
}
