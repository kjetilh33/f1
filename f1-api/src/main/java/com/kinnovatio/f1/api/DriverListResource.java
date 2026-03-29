package com.kinnovatio.f1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.DriverListService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/driver-list")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DriverListResource {
    private static final Logger LOG = Logger.getLogger(DriverListResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DriverListService driverListService;

    @GET
    public String getDriverList() {
        return driverListService.getDriverList()
                .map(root -> {
                    try {
                        return objectMapper.writeValueAsString(root);
                    } catch (JsonProcessingException e) {
                        LOG.warnf("Error getting driver list: %s", e.getMessage());
                        throw new jakarta.ws.rs.ProcessingException("Error getting driver list");
                    }
                })
                .orElseThrow(() -> new NotFoundException("No session status found"));
    }

}
