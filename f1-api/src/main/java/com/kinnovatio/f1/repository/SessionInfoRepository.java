package com.kinnovatio.f1.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.model.SessionInfoRaw;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

@ApplicationScoped
public class SessionInfoRepository {
    private static final Logger LOG = Logger.getLogger(SessionInfoRepository.class);

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.livetiming.session-info.table")
    String sessionInfoTable;

    public Optional<SessionInfoRaw> getSessionInfoLive() {
        String sessionStatusKey = "sessionInfo";

        String sql = """
                Select key, message, message_timestamp, updated_timestamp
                FROM %s
                WHERE key = ?;
                """.formatted(sessionInfoTable);

        try (Connection connection = storageDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, sessionStatusKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String message = resultSet.getString("message");
                    SessionInfoRaw sessionInfo = objectMapper.readValue(message, SessionInfoRaw.class);
                    return Optional.of(sessionInfo);
                }
            }

        } catch (Exception e) {
            LOG.warnf("Error when trying to read session info. Error: %s", e.getMessage());
            throw new RuntimeException("Database error fetching session info", e);
        }

        return Optional.empty();
    }
}
