package com.kinnovatio.f1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.RaceControlMessageService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/race-control-messages")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class RaceControlMessagesResource {
    private static final Logger LOG = Logger.getLogger(RaceControlMessagesResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RaceControlMessageService raceControlMessageService;

    @GET
    public String getRaceControlMessages() {
        try {
            return objectMapper.writeValueAsString(raceControlMessageService.getRaceControlMessages());
        } catch (JsonProcessingException e) {
            LOG.warnf("Error getting race control messages: %s", e.getMessage());
            throw new jakarta.ws.rs.ProcessingException("Error getting race control messages");
        }
    }
}
