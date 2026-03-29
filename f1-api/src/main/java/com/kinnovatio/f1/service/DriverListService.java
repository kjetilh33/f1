package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.model.SessionKeyedMessage;
import com.kinnovatio.f1.model.SessionStatus;
import com.kinnovatio.f1.repository.DriverListRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class DriverListService {
    private static final Logger LOG = Logger.getLogger(DriverListService.class);

    @Inject
    DriverListRepository driverListRepository;

    @Inject
    ObjectMapper objectMapper;

    public Optional<ObjectNode> getDriverList() {
        return driverListRepository.getDriverList().map(sessionKeyedMessage -> {
            try {
                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionKeyedMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionKeyedMessage.sessionId())
                        .set("message", objectMapper.readTree(sessionKeyedMessage.message()));
            } catch (Exception e) {
                LOG.warnf("Error parsing the raw driver list message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
