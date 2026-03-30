package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
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
    private static final String driverListLiveKey = "driverListLive";
    private static final String driverListBaselineKey = "driverListBaseline";

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.driver-list.table")
    String driverListTable;

    public Optional<SessionKeyedMessage> getDriverListLive() {
        return getDriverListByKey(driverListLiveKey);
    }

    public Optional<SessionKeyedMessage> getDriverListBaseline() {
        return getDriverListByKey(driverListBaselineKey);
    }

    private Optional<SessionKeyedMessage> getDriverListByKey(String rowKey) {
        String sql = """
                Select key, session_id, message, message_timestamp, updated_timestamp
                FROM %s
                WHERE key = ?;
                """.formatted(driverListTable);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, rowKey);
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
