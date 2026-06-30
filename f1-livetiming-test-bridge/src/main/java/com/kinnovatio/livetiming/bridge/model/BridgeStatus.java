package com.kinnovatio.livetiming.bridge.model;

import java.time.Instant;

public record BridgeStatus(boolean status, Instant ttl) {
}
