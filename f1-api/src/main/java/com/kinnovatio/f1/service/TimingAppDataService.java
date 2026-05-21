package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.repository.TimingAppDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class TimingAppDataService {
    private static final Logger LOG = Logger.getLogger(TimingAppDataService.class);

    @Inject
    TimingAppDataRepository timingAppDataRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<ObjectNode> getTimingAppData() {
        String baselineTimingDataJson = timingAppDataRepository.getTimingAppDataBaseline()
                .map(SessionKeyedMessage::message)
                .orElseGet(() -> "{}");

        return timingAppDataRepository.getTimingAppDataLive().map(sessionKeyedMessage -> {
            try {
                JsonNode baselineDriverList = objectMapper.readTree(baselineTimingDataJson);
                JsonNode update = objectMapper.readTree(sessionKeyedMessage.message());
                // readerForUpdating modifies 'current' in-place and returns updated version
                JsonNode merged = objectMapper.readerForUpdating(baselineDriverList).readValue(update);

                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionKeyedMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionKeyedMessage.sessionId())
                        .set("message", merged);
            } catch (Exception e) {
                LOG.warnf("Error parsing the raw driver list message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
