package com.kinnovatio.f1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.TimingAppDataService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/timing-app-data")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TimingAppDataResource {
    private static final Logger LOG = Logger.getLogger(TimingAppDataResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TimingAppDataService timingAppDataService;

    @GET
    public String getTimingAppData() {
        return timingAppDataService.getTimingAppData()
                .map(root -> {
                    try {
                        return objectMapper.writeValueAsString(root);
                    } catch (JsonProcessingException e) {
                        LOG.warnf("Error getting timing app data: %s", e.getMessage());
                        throw new jakarta.ws.rs.ProcessingException("Error getting timing app data");
                    }
                })
                .orElse("{}");
    }

}
