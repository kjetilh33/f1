package com.kinnovatio.signalr.messages;

public record LiveTimingHubResponse(Set<LiveTimingMessage> messages, ZonedDateTime timestamp) implements LiveTimingRecord