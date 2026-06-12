package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class TimingStatsRepository {
    private static final String timingStatsLiveKey = "timingStatsLive";
    private static final String timingStatsBaselineKey = "timingStatsBaseline";

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.timing-stats.table")
    String timingStatsTable;

    public Optional<SessionKeyedMessage> getTimingStatsLive() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingStatsTable, timingStatsLiveKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<SessionKeyedMessage> getTimingStatsBaseline() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(timingStatsTable, timingStatsBaselineKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
