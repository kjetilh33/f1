package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;


import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class TimingDataRepository {
    private static final String timingDataLiveKey = "timingDataLive";
    private static final String timingDataBaselineKey = "timingDataBaseline";

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
