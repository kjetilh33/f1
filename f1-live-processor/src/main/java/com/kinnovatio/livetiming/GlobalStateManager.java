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
        UNKNOWN (0, "Unknown state."),
        NO_SESSION (1,"Waiting for next session to start."),
        LIVE_SESSION (2,"Streaming live timing data.");

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
