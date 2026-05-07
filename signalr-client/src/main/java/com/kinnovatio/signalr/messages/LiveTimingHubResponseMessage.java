package com.kinnovatio.signalr.messages;

import java.time.Instant;
import java.util.List;

public record LiveTimingHubResponseMessage(List<LiveTimingMessage> messages, Instant timestamp) implements LiveTimingRecord {

}