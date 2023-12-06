package com.kinnovatio.signalr.messages;

import java.time.ZonedDateTime;

public record LiveTimingMessage(String category, String message, ZonedDateTime timestamp) {
}
