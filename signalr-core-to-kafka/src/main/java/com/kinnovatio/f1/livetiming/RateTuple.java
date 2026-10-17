package com.kinnovatio.f1.livetiming;

import java.time.Instant;

/// A record representing a time-stamped count of events.
///
/// Typically used by [StatsMonitor] to track message throughput counts
/// sampled at specific time intervals (e.g., per second or per minute).
///
/// @param count The number of events recorded in the interval.
/// @param instant The timestamp when the measurement was taken.
public record RateTuple(int count, Instant instant) {
}
