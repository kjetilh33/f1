package com.kinnovatio.f1.livetiming;

import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.time.Instant;
import java.util.List;

public record ConnectorStatus(String connectorState, Instant lastSessionCheck, List<LiveTimingMessage> messages,
                              List<RateTuple> messageRatePerSecond, List<RateTuple> messageRatePerMinute) {
}
