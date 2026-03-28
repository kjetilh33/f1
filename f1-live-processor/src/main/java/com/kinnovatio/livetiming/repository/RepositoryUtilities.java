package com.kinnovatio.livetiming.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
     * Deletes all rows from the specified table.
     * This operation is executed within a transaction.
     *
     * @param tableName the name of the table to clear.
     * @return the number of rows affected by the delete operation.
     * @throws SQLException if a database access error occurs or the SQL execution fails.
     */
    @Transactional
    public int clearAllRowsFromTable(String tableName) throws SQLException {
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
}
