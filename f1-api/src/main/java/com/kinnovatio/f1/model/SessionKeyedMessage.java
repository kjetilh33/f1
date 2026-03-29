package com.kinnovatio.f1.model;

import java.time.Instant;

public record SessionKeyedMessage(String key, int sessionId, String message, Instant messageTimestamp,
                                  Instant updatedTimestamp) {
}
