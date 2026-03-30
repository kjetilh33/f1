package com.kinnovatio.livetiming.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for managing database tables used for storing live timing messages.
 * This class provides methods to initialize table schemas and perform maintenance operations
 * such as clearing table contents.
 */
@ApplicationScoped
public class RepositoryUtilities {
    private static final Logger LOG = Logger.getLogger(RepositoryUtilities.class);

    @Inject
    AgroalDataSource storageDataSource;

    /**
     * Creates a database table for storing multiple messages using an auto-incrementing primary key.
     * The schema includes an ID, session ID, JSONB message content, and timestamps.
     *
     * @param tableName the name of the table to be created.
     * @throws SQLException if a database access error occurs or the SQL execution fails.
     */
    public void createMultiMessageDbTableIfNotExists(String tableName) throws SQLException {
        // IMPORTANT: Validate the table name against a predefined list
        // or a strict pattern to prevent SQL injection.
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id SERIAL PRIMARY KEY,
                    session_id INT DEFAULT -1,
                    message JSONB,
                    message_timestamp TIMESTAMPTZ,
                    updated_timestamp TIMESTAMPTZ DEFAULT NOW()
                );
                """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            LOG.infof("Successfully created (if not already exists) the DB table: %s", tableName);
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s. Error: %s", tableName, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a database table for storing messages identified by a unique key.
     * The schema includes a VARCHAR primary key, session ID, JSONB message content, and timestamps.
     *
     * @param tableName the name of the table to be created.
     * @throws SQLException if a database access error occurs or the SQL execution fails.
     */
    public void createKeyedMessageDbTableIfNotExists(String tableName) throws SQLException {
        // IMPORTANT: Validate the table name against a predefined list
        // or a strict pattern to prevent SQL injection.
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    key VARCHAR(100) PRIMARY KEY,
                    session_id INT DEFAULT -1,
                    message JSONB,
                    message_timestamp TIMESTAMPTZ,
                    updated_timestamp TIMESTAMPTZ DEFAULT NOW()
                );
                """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            LOG.infof("Successfully created (if not already exists) the DB table: %s", tableName);
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s. Error: %s", tableName, e.getMessage());
            throw e;
        }
    }

    /**
     * Stores a message into a keyed table using an upsert operation.
     * If a record with the same key already exists, it updates the existing record's message,
     * message timestamp, and updated timestamp.
     *
     * @param tableName the name of the table where the message will be stored.
     * @param rowKey the unique key identifying the message.
     * @param sessionId the session identifier.
     * @param message the JSONB message content.
     * @param messageTimestamp the timestamp of the message.
     * @throws Exception if a database access error occurs or the SQL execution fails.
     */
    public void storeIntoKeyedMessageTable(String tableName, String rowKey, int sessionId, String message, Instant messageTimestamp) throws Exception {
        // IMPORTANT: Validate the table name against a predefined list
        // or a strict pattern to prevent SQL injection.
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        String upsertMessageSql = """
            INSERT INTO %s (key, session_id, message, message_timestamp, updated_timestamp) 
            VALUES (?, ?, ?::jsonb, ?::timestamptz, NOW())
            ON CONFLICT (key)
            DO UPDATE SET
                message = EXCLUDED.message,
                message_timestamp = EXCLUDED.message_timestamp,
                updated_timestamp = EXCLUDED.updated_timestamp;
            """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertMessageSql)) {
            statement.setString(1, rowKey);
            statement.setInt(2, sessionId);
            statement.setString(3, message);
            statement.setObject(4, OffsetDateTime.ofInstant(messageTimestamp, ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to store message into the %s table. Error: %s",
                    tableName,
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes all rows from the specified table.
     * This operation is executed within a transaction.
     *
     * @param tableName the name of the table to clear.
     * @return the number of rows affected by the delete operation.
     * @throws SQLException if a database access error occurs or the SQL execution fails.
     */
    @Transactional
    public int clearAllRowsFromTable(String tableName) throws SQLException {
        // IMPORTANT: Validate the table name against a predefined list
        // or a strict pattern to prevent SQL injection.
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        int rowsAffected = -1;
        String sql = """
                    DELETE FROM %s;
                    """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            rowsAffected = statement.executeUpdate(sql);
        } catch (Exception e) {
            LOG.warnf("Error when trying to clear the %s table. Error: %s",
                    tableName,
                    e.getMessage());
            throw e;
        }

        return rowsAffected;
    }

    /**
     * Deletes row matching the key from the specified table.
     * This operation is executed within a transaction.
     *
     * @param tableName the name of the table to clear.
     * @param key the key matching the row to clear.
     * @return the number of rows affected by the delete operation.
     * @throws SQLException if a database access error occurs or the SQL execution fails.
     */
    @Transactional
    public int clearRowFromKeyedTable(String tableName, String key) throws SQLException {
        int rowsAffected = -1;
        String sql = """
                    DELETE FROM %s
                    WHERE key = ?;
                    """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            rowsAffected = preparedStatement.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Error when trying to clear row with key %s from the %s table. Error: %s",
                    key,
                    tableName,
                    e.getMessage());
            throw e;
        }

        return rowsAffected;
    }

    // A simple validation method
    private boolean isValidTableName(String name) {
        // Only allow alphanumeric characters and underscores to be safe
        return name.matches("[A-Za-z0-9_]+");
    }
}
