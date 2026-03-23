package com.kinnovatio.f1.model;

import java.time.Instant;

public record SessionMessage(int id, int sessionId, String message, Instant messageTimestamp,
                             Instant updatedTimestamp) {
}
