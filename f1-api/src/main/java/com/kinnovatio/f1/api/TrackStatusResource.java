package com.kinnovatio.f1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.RaceControlMessageService;
import com.kinnovatio.f1.service.TrackStatusService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/track-status")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TrackStatusResource {
    private static final Logger LOG = Logger.getLogger(TrackStatusResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TrackStatusService trackStatusService;

    @GET
    public String getRaceControlMessages() {
        try {
            return objectMapper.writeValueAsString(trackStatusService.getTrackStatusHistory());
        } catch (JsonProcessingException e) {
            LOG.warnf("Error getting track status messages: %s", e.getMessage());
            throw new jakarta.ws.rs.ProcessingException("Error getting track status messages");
        }
    }
}
