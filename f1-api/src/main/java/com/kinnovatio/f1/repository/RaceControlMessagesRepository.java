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
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RaceControlMessagesRepository {
    private static final Logger LOG = Logger.getLogger(RaceControlMessagesRepository.class);

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.race-control-message.table")
    String raceControlMessageTable;

    public List<SessionMessage> getRaceControlMessages() {
        int limit = 1000;
        List<SessionMessage> returnList = new ArrayList<>();

        String sql = """
                Select id, session_key, message, message_timestamp, updated_timestamp
                FROM %s
                limit %d;
                """.formatted(raceControlMessageTable, limit);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    int sessionId = resultSet.getInt("session_id");
                    String message = resultSet.getString("message");
                    Instant messageTimestamp = resultSet.getObject("message_timestamp", Instant.class);
                    Instant updatedTimestamp = resultSet.getObject("updated_timestamp", Instant.class);
                    returnList.add(new SessionMessage(id, sessionId, message, messageTimestamp, updatedTimestamp));
                }
            }

        } catch (Exception e) {
            LOG.warnf("Error when trying to read race control messages. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching race control messages", e);
        }

        // Check if we are close to the limit on number of results
        if (returnList.size() == limit) {
            LOG.warnf("Number of results from table %s is at the limit of %d items. Please check the database",
                    raceControlMessageTable, limit);
        }
        return returnList;
    }
}
