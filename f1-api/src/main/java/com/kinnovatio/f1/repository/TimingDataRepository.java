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
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@ApplicationScoped
public class TimingDataRepository {
    private static final Logger LOG = Logger.getLogger(TimingDataRepository.class);
    private static final String timingDataLiveKey = "timingDataLive";
    private static final String timingDataBaselineKey = "timingDataBaseline";

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.timing-data.table")
    String timingDataTable;

    public Optional<SessionKeyedMessage> getTimingDataLive() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingDataTable, timingDataLiveKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<SessionKeyedMessage> getTimingDataBaseline() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingDataTable, timingDataBaselineKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
