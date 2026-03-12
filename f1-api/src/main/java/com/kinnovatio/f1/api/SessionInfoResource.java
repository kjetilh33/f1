package com.kinnovatio.f1.api;

import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.model.SessionStatus;
import com.kinnovatio.f1.repository.SessionInfoRepository;
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
@Path("live")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class SessionInfoResource {
    private static final Logger LOG = Logger.getLogger(SessionInfoResource.class);

    @Inject
    SessionInfoRepository sessionInfoRepository;

    @GET
    public SessionStatus getSessionStatus() {
        return new SessionStatus("test");
    }

    @GET
    @Path("/session-info")
    public SessionInfoRaw getSessionInfoLive() {
        return sessionInfoRepository.getSessionInfoLive()
                .orElseThrow(() -> new NotFoundException("No session info found"));
    }
}
