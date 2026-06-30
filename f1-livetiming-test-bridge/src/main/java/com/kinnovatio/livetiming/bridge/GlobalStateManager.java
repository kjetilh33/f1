package com.kinnovatio.livetiming.bridge;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/// Manages the global state of the live timing application.
///
/// This class maintains thread-safe references to the current session status,
/// the unique session identifier (key), and the timestamp of the last received data packet.
@ApplicationScoped
public class GlobalStateManager {
    private static final Logger LOG = Logger.getLogger(GlobalStateManager.class);

    private final AtomicBoolean enableBridge = new AtomicBoolean(false);
    private final AtomicBoolean enableTimer = new AtomicBoolean(false);
    private final AtomicReference<Instant> ttl = new AtomicReference<>(Instant.EPOCH);

    private final Duration maxDuration = Duration.ofHours(5);

    @Inject
    MeterRegistry registry;

    // Lifecycle hook triggers automatically on application startup
    void onStart(@Observes StartupEvent ev) {
        Gauge.builder("livetiming_test_bridge_enabled", enableBridge, bol -> bol.get() ? 1 : 0)
                .description("Tracks if the bridge enabled or disabled")
                .register(registry);
    }


    public boolean isBridgeEnabled() {
        return enableBridge.get();
    }

    public void enableBridge() {
        enableBridge(maxDuration);
    }

    public void enableBridge(Duration ttlDuration) {
        setEnableBridge(true);
        setTimer(ttlDuration);
    }

    public void disableBridge() {
        setEnableBridge(false);
        enableTimer.set(false);
    }

    private void setEnableBridge(boolean enable) {
        enableBridge.set(enable);
    }

    public Instant setTimer(Duration duration) {
        Duration ttlDuration = duration;
        if (ttlDuration.compareTo(maxDuration) > 0) {
            ttlDuration = maxDuration;
            LOG.infof("Trying to set a too long ttl timer: %s. Will set ttl to its max duration: %s",
                    duration,
                    ttlDuration);
        }
        Instant newTtl = Instant.now().plus(ttlDuration);
        enableTimer.set(true);
        ttl.set(newTtl);

        LOG.infof("Setting new timer with a time to live of %s", ttlDuration);
        return newTtl;
    }

    public Instant getTtl() {
        return ttl.get();
    }

    @Scheduled(every = "2s", delay = 5, delayUnit = TimeUnit.SECONDS)
    @RunOnVirtualThread
    void checkTtl() {
        if (enableTimer.get() && ttl.get().isAfter(Instant.now())) {
            setEnableBridge(false);
            LOG.infof("Bridge timer expired. Disabling bridge.");
        }

    }
}
