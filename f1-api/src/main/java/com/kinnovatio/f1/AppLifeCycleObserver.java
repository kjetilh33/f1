package com.kinnovatio.f1;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AppLifeCycleObserver {
    private static final Logger LOG = Logger.getLogger(AppLifeCycleObserver.class);

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.log.source")
    String logSource;

    void onStart(@Observes StartupEvent ev) {
        // This runs when the application is starting.
        LOG.infof("Starting the live timing api...");
        LOG.infof("Config picked up from %s", logSource);

        LOG.infof("The api is ready. Waiting for live timing messages...");
    }

    void onStop(@Observes ShutdownEvent ev) {
        // Cleanup logic
    }
}
