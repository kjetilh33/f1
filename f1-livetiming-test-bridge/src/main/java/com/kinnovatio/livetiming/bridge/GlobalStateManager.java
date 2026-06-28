package com.kinnovatio.livetiming.bridge;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
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

    public boolean isBrideEnabled() {
        return enableBridge.get();
    }

    public void setEnableBridge(boolean enable) {
        enableBridge.set(enable);

        if (!enable) {
            enableTimer.set(false);
        }
    }

    public void setEnableBridge(boolean enableBridge, Duration ttlDuration) {
        setEnableBridge(enableBridge);

        if (enableBridge) {
            setTimer(ttlDuration);
        }
    }

    public void setTimer(Duration duration) {
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
    }

    @Scheduled(every = "2s", delay = 5, delayUnit = TimeUnit.SECONDS)
    void checkTtl() {

    }
}
