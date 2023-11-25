package com.kinnovatio.signalr.messages;

import java.time.ZonedDateTime;

public record Message(String category, String message, ZonedDateTime timestamp) {
}
