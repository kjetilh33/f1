package com.kinnovatio.f1.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.model.SessionMessage;
import com.kinnovatio.f1.model.SessionStatus;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@ApplicationScoped
public class DriverListRepository {
    private static final Logger LOG = Logger.getLogger(DriverListRepository.class);

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.driver-list.table")
    String driverListTable;

    public Optional<SessionKeyedMessage> getDriverList() {
        String driverListKey = "driverList";

        String sql = """
                Select key, session_id, message, message_timestamp, updated_timestamp
                FROM %s
                WHERE key = ?;
                """.formatted(driverListTable);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, driverListKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String key = resultSet.getString("Key");
                    int sessionId = resultSet.getInt("session_id");
                    String message = resultSet.getString("message");
                    Instant messageTimestamp =
                            resultSet.getObject("message_timestamp", OffsetDateTime.class).toInstant();
                    Instant updatedTimestamp =
                            resultSet.getObject("updated_timestamp", OffsetDateTime.class).toInstant();
                    return Optional.of(new SessionKeyedMessage(key, sessionId, message, messageTimestamp, updatedTimestamp));
                }
            }

        } catch (Exception e) {
            LOG.warnf("Error when trying to read driver list. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching driver list", e);
        }

        return Optional.empty();
    }
}
