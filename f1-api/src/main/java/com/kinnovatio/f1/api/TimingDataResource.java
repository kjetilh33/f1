package com.kinnovatio.f1.api;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.TimingDataService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/timing-data")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TimingDataResource {
    private static final Logger LOG = Logger.getLogger(TimingDataResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TimingDataService timingDataService;

    @GET
    public String getTimingData() {
        return timingDataService.getTimingData()
                .map(root -> {
                    try {
                        return objectMapper.writeValueAsString(root);
                    } catch (JacksonException e) {
                        LOG.warnf("Error getting timing data: %s", e.getMessage());
                        throw new jakarta.ws.rs.ProcessingException("Error getting timing data");
                    }
                })
                .orElse("{}");
    }

}
