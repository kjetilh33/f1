package com.kinnovatio.livetiming;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/// Manages the global state of the live timing application.
///
/// This class maintains thread-safe references to the current session status,
/// the unique session identifier (key), and the timestamp of the last received data packet.
@ApplicationScoped
public class GlobalStateManager {
    private final AtomicReference<SessionState> sessionState = new AtomicReference<>(SessionState.UNKNOWN);
    private final AtomicInteger sessionKey = new AtomicInteger(-1);
    private final AtomicReference<Instant> lastMessageReceived = new AtomicReference<>(Instant.now());

    /// Retrieves the current state of the F1 session.
    ///
    /// @return The current {@link SessionState}.
    public SessionState getSessionState() {
        return sessionState.get();
    }

    /// Updates the session state.
    ///
    /// @param state The new {@link SessionState} to set.
    public void setSessionState(SessionState state) {
        sessionState.set(state);
    }

    /// Retrieves the unique session key.
    ///
    /// @return The session key integer, or -1 if not set.
    public int getSessionKey() {
        return sessionKey.get();
    }

    /// Updates the unique session key.
    ///
    /// @param key The new session key integer.
    public void setSessionKey(int key) {
        sessionKey.set(key);
    }

    /// Gets the timestamp of the last successfully received message.
    ///
    /// @return The {@link Instant} of the last message.
    public Instant getLastMessageReceived() {
        return lastMessageReceived.get();
    }

    private void setLastMessageReceived(Instant instant) {
        lastMessageReceived.set(instant);
    }

    /// Updates the last message received timestamp to the current system time.
    ///
    /// This method is called to indicate liveness of the data stream.
    public void registerMessageReceived() {
        lastMessageReceived.set(Instant.now());
    }

    /// Enumeration representing the possible states of a racing session.
    public enum SessionState {
        /// State is not yet determined.
        UNKNOWN (0, "Unknown"),
        /// No active session is currently running.
        NO_SESSION (1,"No session"),
        /// A session is currently live and data is being processed.
        LIVE_SESSION (2,"Session ongoing"),
        /// The system is inactive or tracking has been disabled.
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
