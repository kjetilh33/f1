package com.kinnovatio.f1.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.f1.model.SessionMessage;
import com.kinnovatio.f1.repository.RaceControlMessagesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class RaceControlMessageService {
    private static final Logger LOG = Logger.getLogger(RaceControlMessageService.class);

    @Inject
    RaceControlMessagesRepository raceControlMessagesRepository;

    @Inject
    ObjectMapper objectMapper;

    public ObjectNode getRaceControlMessages() {
        List<SessionMessage> raceControlMessages = raceControlMessagesRepository.getRaceControlMessages();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = root.putArray("messages");
        for (SessionMessage message : raceControlMessages) {
            try {
                messages.add(objectMapper.readTree(message.message()));
            } catch (JsonProcessingException e) {
                LOG.warnf("Error parsing the raw race control message into a Json tree structure. Error: %s",
                        e.getMessage());
                throw new RuntimeException(e);
            }

        }

        return root;
    }

}
