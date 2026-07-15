package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionMessage;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TrackStatusRepository {
    private static final Logger LOG = Logger.getLogger(TrackStatusRepository.class);

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.track-status.table")
    String trackStatusTable;

    public List<SessionMessage> getTrackStatusHistory() {
        int limit = 1000;
        List<SessionMessage> returnList = new ArrayList<>();

        String sql = """
                Select id, session_id, message, message_timestamp, updated_timestamp
                FROM %s
                limit %d;
                """.formatted(trackStatusTable, limit);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    int sessionId = resultSet.getInt("session_id");
                    String message = resultSet.getString("message");
                    Instant messageTimestamp = resultSet.getObject("message_timestamp", OffsetDateTime.class).toInstant();
                    Instant updatedTimestamp = resultSet.getObject("updated_timestamp", OffsetDateTime.class).toInstant();
                    returnList.add(new SessionMessage(id, sessionId, message, messageTimestamp, updatedTimestamp));

                    // Check if we are close to the limit on number of results
                    if (returnList.size() == limit) {
                        LOG.warnf("Number of results from table %s is at the limit of %d items. Please check the database",
                                trackStatusTable, limit);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error when trying to read track status. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching track status", e);
        }

        return returnList;
    }

    public Optional<SessionMessage> getTrackStatus() {
        String sql = """
                Select id, session_id, message, message_timestamp, updated_timestamp
                FROM %s
                order by updated_timestamp desc
                limit 1;
                """.formatted(trackStatusTable);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    int sessionId = resultSet.getInt("session_id");
                    String message = resultSet.getString("message");
                    Instant messageTimestamp = resultSet.getObject("message_timestamp", OffsetDateTime.class).toInstant();
                    Instant updatedTimestamp = resultSet.getObject("updated_timestamp", OffsetDateTime.class).toInstant();
                    return Optional.of(new SessionMessage(id, sessionId, message, messageTimestamp, updatedTimestamp));
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error when trying to read track status. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching track status", e);
        }

        return Optional.empty();
    }
}
