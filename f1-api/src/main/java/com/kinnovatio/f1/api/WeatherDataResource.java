package com.kinnovatio.f1.api;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kinnovatio.f1.service.WeatherDataService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("live/weather-data")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class WeatherDataResource {
    private static final Logger LOG = Logger.getLogger(WeatherDataResource.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    WeatherDataService weatherDataService;

    @GET
    public String getWeatherData() {
        return weatherDataService.getWeatherData()
                .map(root -> {
                    try {
                        return objectMapper.writeValueAsString(root);
                    } catch (JacksonException e) {
                        LOG.warnf("Error getting timing data: %s", e.getMessage());
                        throw new ProcessingException("Error getting timing data");
                    }
                })
                .orElse("{}");
    }

}
