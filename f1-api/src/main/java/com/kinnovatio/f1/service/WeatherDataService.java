package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.repository.WeatherDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class WeatherDataService {
    private static final Logger LOG = Logger.getLogger(WeatherDataService.class);

    @Inject
    WeatherDataRepository weatherDataRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<ObjectNode> getWeatherData() {
        return weatherDataRepository.getWeatherData().map(sessionKeyedMessage -> {
            try {
                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionKeyedMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionKeyedMessage.sessionId())
                        .set("message", objectMapper.readTree(sessionKeyedMessage.message()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
