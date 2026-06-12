package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.repository.TimingStatsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class TimingStatsService {
    private static final Logger LOG = Logger.getLogger(TimingStatsService.class);

    @Inject
    TimingStatsRepository timingStatsRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<ObjectNode> getTimingStats() {
        String baselineTimingStatsJson = timingStatsRepository.getTimingStatsBaseline()
                .map(SessionKeyedMessage::message)
                .orElseGet(() -> "{}");

        return timingStatsRepository.getTimingStatsLive().map(sessionKeyedMessage -> {
            try {
                JsonNode baselineTimingStats = objectMapper.readTree(baselineTimingStatsJson);
                JsonNode update = objectMapper.readTree(sessionKeyedMessage.message());
                // readerForUpdating modifies 'current' in-place and returns updated version
                JsonNode merged = objectMapper.readerForUpdating(baselineTimingStats).readValue(update);

                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionKeyedMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionKeyedMessage.sessionId())
                        .set("message", merged);
            } catch (Exception e) {
                LOG.warnf("Error parsing the raw timing stats message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
