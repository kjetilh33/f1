package com.kinnovatio.signalr.messages;

import java.time.ZonedDateTime;
import java.util.List;

public record LiveTimingHubResponseMessage(List<LiveTimingMessage> messages, ZonedDateTime timestamp) implements LiveTimingRecord {

}