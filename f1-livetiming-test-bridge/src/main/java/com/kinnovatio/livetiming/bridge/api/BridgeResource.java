package com.kinnovatio.livetiming.bridge.api;

import com.kinnovatio.livetiming.bridge.GlobalStateManager;
import com.kinnovatio.livetiming.bridge.model.BridgeStatus;
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
    @Path("status")
    public BridgeStatus getRootStatus() {
        return new BridgeStatus(stateManager.isBridgeEnabled(), stateManager.getTtl());

    }

    @GET
    @Path("enable")
    public BridgeStatus setEnabled() {
        stateManager.enableBridge();
        LOG.infof("Enabling bridge.");
        return new BridgeStatus(stateManager.isBridgeEnabled(), stateManager.getTtl());
    }

    @GET
    @Path("disable")
    public BridgeStatus setDisabled() {
        stateManager.disableBridge();
        LOG.infof("Disabling bridge.");
        return new BridgeStatus(stateManager.isBridgeEnabled(), stateManager.getTtl());
    }
}
