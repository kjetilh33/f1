package com.kinnovatio.livetiming.bridge;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;


@ApplicationScoped
public class AppLifeCycleObserver {
    private static final Logger LOG = Logger.getLogger(AppLifeCycleObserver.class);

    @Inject
    GlobalStateManager stateManager;

    void onStart(@Observes StartupEvent ev) {
        // This runs when the application is starting.
        LOG.infof("Starting the livetiming test bridge processor...");
        if (stateManager.isBridgeEnabled()) {
            LOG.infof("When ready, the bridge is enabled and will start forwarding messages.");
        } else {
            LOG.infof("When ready, the bridge is disabled and will not forward messages.");
        }

        LOG.infof("The processor is ready. Waiting for live timing messages...");
    }

    void onStop(@Observes ShutdownEvent ev) {
        // Cleanup logic
        LOG.infof("Shutting down the livetiming test bridge processor...");
    }
}
