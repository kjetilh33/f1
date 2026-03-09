package com.kinnovatio.livetiming;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class GlobalStateManager {
    private final AtomicReference<SessionState> sessionState = new AtomicReference<>(SessionState.UNKNOWN);
    private final AtomicInteger sessionKey = new AtomicInteger(-1);

    public SessionState getSessionState() {
        return sessionState.get();
    }

    public void setSessionState(SessionState state) {
        sessionState.set(state);
    }

    public int getSessionKey() {
        return sessionKey.get();
    }

    public void setSessionKey(int key) {
        sessionKey.set(key);
    }

    public enum SessionState {
        UNKNOWN (0, "Unknown"),
        NO_SESSION (1,"No session"),
        LIVE_SESSION (2,"Session ongoing"),
        INACTIVE (3, "Inactive");

        private final int statusValue;
        private final String status;

        public int getStatusValue() {
            return statusValue;
        }

        public String getStatus() {
            return status;
        }

        SessionState(int statusValue,String status) {
            this.statusValue = statusValue;
            this.status = status;
        }
    }
}
