package com.kinnovatio.livetiming;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class GlobalStateManager {
    private final AtomicReference<SessionState> sessionState = new AtomicReference<>(SessionState.UNKNOWN);

    public SessionState getSessionState() {
        return sessionState.get();
    }

    public void setSessionState(SessionState state) {
        sessionState.set(state);
    }

    public enum SessionState {
        UNKNOWN (0, "Unknown."),
        NO_SESSION (1,"No session."),
        LIVE_SESSION (2,"Session ongoing.");

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
