package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class DriverListRepository {
    private static final String driverListLiveKey = "driverListLive";
    private static final String driverListBaselineKey = "driverListBaseline";

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.driver-list.table")
    String driverListTable;

    public Optional<SessionKeyedMessage> getDriverListLive() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(driverListTable, driverListLiveKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<SessionKeyedMessage> getDriverListBaseline() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(driverListTable, driverListBaselineKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
