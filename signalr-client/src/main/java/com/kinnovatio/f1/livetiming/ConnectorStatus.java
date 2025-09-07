package com.kinnovatio.f1.livetiming;

import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.util.List;

public record ConnectorStatus(String connectorState, List<LiveTimingMessage> messages) {
}
