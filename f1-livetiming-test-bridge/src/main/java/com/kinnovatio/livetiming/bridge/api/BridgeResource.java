package com.kinnovatio.livetiming.bridge.api;

import com.kinnovatio.livetiming.bridge.GlobalStateManager;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

@ApplicationScoped
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class BridgeResource {
    private static final Logger LOG = Logger.getLogger(BridgeResource.class);

    @Inject
    GlobalStateManager stateManager;

    @GET
    public boolean getRootStatus() {
        return stateManager.isBrideEnabled();
    }

    @GET
    @Path("enable")
    public boolean setEnabled() {
        stateManager.setEnableBridge(true);
        LOG.infof("Enabling bridge.");
        return stateManager.isBrideEnabled();
    }

    @GET
    @Path("disable")
    public boolean setDisabled() {
        stateManager.setEnableBridge(false);
        LOG.infof("Disabling bridge.");
        return stateManager.isBrideEnabled();
    }
}
