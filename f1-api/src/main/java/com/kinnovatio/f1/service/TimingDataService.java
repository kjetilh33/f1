package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.repository.TimingDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class TimingDataService {
    private static final Logger LOG = Logger.getLogger(TimingDataService.class);

    @Inject
    TimingDataRepository timingDataRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<ObjectNode> getTimingData() {
        String baselineTimingDataJson = timingDataRepository.getTimingDataBaseline()
                .map(SessionKeyedMessage::message)
                .orElseGet(() -> "{}");

        return timingDataRepository.getTimingDataLive().map(sessionKeyedMessage -> {
            try {
                JsonNode baselineTimingData = objectMapper.readTree(baselineTimingDataJson);
                JsonNode update = objectMapper.readTree(sessionKeyedMessage.message());
                // readerForUpdating modifies 'current' in-place and returns updated version
                JsonNode merged = objectMapper.readerForUpdating(baselineTimingData).readValue(update);

                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionKeyedMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionKeyedMessage.sessionId())
                        .set("message", merged);
            } catch (Exception e) {
                LOG.warnf("Error parsing the raw timing data message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
