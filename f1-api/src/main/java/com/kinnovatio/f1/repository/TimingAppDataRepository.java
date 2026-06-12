package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class TimingAppDataRepository {
    private static final String timingAppDataLiveKey = "timingAppDataLive";
    private static final String timingAppDataBaselineKey = "timingAppDataBaseline";

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.timing-app-data.table")
    String timingAppDataTable;

    public Optional<SessionKeyedMessage> getTimingAppDataLive() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingAppDataTable, timingAppDataLiveKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<SessionKeyedMessage> getTimingAppDataBaseline() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingAppDataTable, timingAppDataBaselineKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
