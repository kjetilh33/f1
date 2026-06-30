package com.kinnovatio.livetiming.bridge.api;

import com.kinnovatio.livetiming.bridge.GlobalStateManager;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class BridgeResource {
    private static final Logger LOG = Logger.getLogger(BridgeResource.class);

    @Inject
    GlobalStateManager stateManager;

    @GET
    public Response getRootStatus() {
        return Response.ok(Map.of("message", "Bridge enabled: " + stateManager.isBridgeEnabled())).build();

    }

    @GET
    @Path("enable")
    public Response setEnabled() {
        stateManager.enableBridge();
        LOG.infof("Enabling bridge.");
        return Response.ok(Map.of("message", "Bridge enabled: " + stateManager.isBridgeEnabled())).build();
    }

    @GET
    @Path("disable")
    public Response setDisabled() {
        stateManager.disableBridge();
        LOG.infof("Disabling bridge.");
        return Response.ok(Map.of("message", "Bridge enabled: " + stateManager.isBridgeEnabled())).build();

    }
}
