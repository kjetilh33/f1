package com.kinnovatio.livetiming.bridge;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/// Manages the global state of the live timing application.
///
/// This class maintains thread-safe references to the current session status,
/// the unique session identifier (key), and the timestamp of the last received data packet.
@ApplicationScoped
public class GlobalStateManager {
    private final AtomicBoolean enableBridge = new AtomicBoolean(false);

    public boolean isBrideEnabled() {
        return enableBridge.get();
    }

    public void setEnableBridge(boolean enable) {
        enableBridge.set(enable);
    }
}
