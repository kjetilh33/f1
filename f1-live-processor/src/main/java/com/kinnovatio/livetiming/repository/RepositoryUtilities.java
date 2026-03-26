package com.kinnovatio.livetiming.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
public class RepositoryUtilities {
    private static final Logger LOG = Logger.getLogger(RepositoryUtilities.class);

    @Inject
    AgroalDataSource storageDataSource;

    public void createMultiMessageDbTableIfNotExists(String tableName) throws SQLException {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    message_id SERIAL PRIMARY KEY,
                    session_key INT DEFAULT -1,
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

    public void createKeyMessageDbTableIfNotExists(String tableName) throws SQLException {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    key VARCHAR(100) PRIMARY KEY,
                    session_key INT DEFAULT -1,
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
