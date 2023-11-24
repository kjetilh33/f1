package com.kinnovatio.signalr;

import java.time.ZonedDateTime;

public record Message(String category, String message, ZonedDateTime timestamp) {
}
