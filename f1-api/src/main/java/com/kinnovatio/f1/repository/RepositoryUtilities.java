package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@ApplicationScoped
public class RepositoryUtilities {
    private static final Logger LOG = Logger.getLogger(RepositoryUtilities.class);

    @Inject
    AgroalDataSource storageDataSource;

    public Optional<SessionKeyedMessage> getRowFromKeyedTable(String tableName, String rowKey) throws SQLException {
        // IMPORTANT: Validate the table name against a predefined list
        // or a strict pattern to prevent SQL injection.
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        String sql = """
                Select key, session_id, message, message_timestamp, updated_timestamp
                FROM %s
                WHERE key = ?;
                """.formatted(tableName);

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
            LOG.warnf("Error when trying to read from %s table. Error: %s", tableName, e.getMessage());
            throw e;
        }

        return Optional.empty();
    }


    // A simple validation method
    private boolean isValidTableName(String name) {
        // Only allow alphanumeric characters and underscores to be safe
        return name.matches("[A-Za-z0-9_]+");
    }
}
