package com.kinnovatio.f1.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionMessage;
import com.kinnovatio.f1.repository.RaceControlMessagesRepository;
import com.kinnovatio.f1.repository.TrackStatusRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class TrackStatusService {
    private static final Logger LOG = Logger.getLogger(TrackStatusService.class);

    @Inject
    TrackStatusRepository trackStatusRepository;

    @Inject
    ObjectMapper objectMapper;

    public ObjectNode getTrackStatus() {
        ObjectNode fallback = objectMapper.createObjectNode()
                .put("updatedTimestamp", Instant.now().toString())
                .put("sessionId", -1)
                .set("message", objectMapper.createObjectNode()
                        .put("status", -1)
                        .put("message", "Unknown"));

        return trackStatusRepository.getTrackStatus().map(sessionMessage -> {
            try {
                return objectMapper.createObjectNode()
                        .put("updatedTimestamp", sessionMessage.updatedTimestamp().toString())
                        .put("sessionId", sessionMessage.sessionId())
                        .put("message", sessionMessage.message());
            } catch (Exception e) {
                LOG.warnf("Error parsing the track status message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        }).orElse(fallback);
    }

    public ObjectNode getTrackStatusHistory() {
        List<SessionMessage> trackStatusMessages = trackStatusRepository.getTrackStatusHistory();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = root.putArray("messages");

        Set<Integer> sessionIds = new HashSet<>();

        for (SessionMessage message : trackStatusMessages) {
            try {
                messages.add(objectMapper.readTree(message.message()));
                sessionIds.add(message.sessionId());
            } catch (JsonProcessingException e) {
                LOG.warnf("Error parsing the raw track status message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }
        }

        // If the results set contain more than a single session_id, flag a warning
        if (sessionIds.size() > 1) {
            LOG.warnf("The track status messages contain more than one distinct session id: %s",
                    sessionIds.toString());
        }

        return root;
    }

}
