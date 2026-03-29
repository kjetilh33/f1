package com.kinnovatio.livetiming.model;

import com.kinnovatio.livetiming.GlobalStateManager;

public record SessionStateUpdate(GlobalStateManager.SessionState oldState, GlobalStateManager.SessionState newState) {
}
