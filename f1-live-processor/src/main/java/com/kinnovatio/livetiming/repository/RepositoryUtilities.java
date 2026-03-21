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
