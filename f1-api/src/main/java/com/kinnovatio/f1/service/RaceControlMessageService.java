package com.kinnovatio.f1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.model.SessionMessage;
import com.kinnovatio.f1.model.SessionStatus;
import com.kinnovatio.f1.repository.RaceControlMessagesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RaceControlMessageService {
    private static final Logger LOG = Logger.getLogger(RaceControlMessageService.class);

    @Inject
    RaceControlMessagesRepository raceControlMessagesRepository;

    @Inject
    ObjectMapper objectMapper;

    public String getRaceControlMessages() {
        List<SessionMessage> raceControlMessages = raceControlMessagesRepository.getRaceControlMessages();


        return null;
    }

}
