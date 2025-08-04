package com.kinnovatio.signalr.messages;

import java.time.ZonedDateTime;
import java.util.Set;

public record LiveTimingHubResponse(Set<LiveTimingMessage> messages, ZonedDateTime timestamp) implements LiveTimingRecord {

}