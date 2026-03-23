package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.model.SessionMessage;
import com.kinnovatio.f1.model.SessionStatus;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                Select message_id, session_key, message, message_timestamp, updated_timestamp
                FROM %s
                limit %d;
                """.formatted(raceControlMessageTable, limit);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("message_id");
                    int sessionId = resultSet.getInt("session_id");
                    String message = resultSet.getString("message");
                    Instant messageTimestamp = resultSet.getObject("message_timestamp", Instant.class);
                    Instant updatedTimestamp = resultSet.getObject("updated_timestamp", Instant.class);
                    returnList.add(new SessionMessage(id, sessionId, message, messageTimestamp, updatedTimestamp));
                }
            }

        } catch (Exception e) {
            LOG.warnf("Error when trying to read session info. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching session info", e);
        }

        return returnList;
    }
}
