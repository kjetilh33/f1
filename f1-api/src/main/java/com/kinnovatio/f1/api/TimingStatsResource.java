package com.kinnovatio.f1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.TimingStatsService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/timing-stats")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TimingStatsResource {
    private static final Logger LOG = Logger.getLogger(TimingStatsResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TimingStatsService timingStatsService;

    @GET
    public String getTimingStats() {
        return timingStatsService.getTimingStats()
                .map(root -> {
                    try {
                        return objectMapper.writeValueAsString(root);
                    } catch (JsonProcessingException e) {
                        LOG.warnf("Error getting timing stats: %s", e.getMessage());
                        throw new jakarta.ws.rs.ProcessingException("Error getting timing stats");
                    }
                })
                .orElse("{}");
    }

}
