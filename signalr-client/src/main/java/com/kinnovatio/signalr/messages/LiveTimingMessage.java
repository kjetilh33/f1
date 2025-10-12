package com.kinnovatio.signalr.messages;

import java.time.ZonedDateTime;

/// Represents a single, parsed data message from the Formula 1 live timing SignalR feed.
///
/// This record encapsulates a piece of live timing information, such as car data,
/// timing stats, or session information, along with its associated metadata.
///
/// @param category  The category of the data (e.g., "TimingData", "CarData.z", "SessionInfo").
///                  This corresponds to a specific data stream from the F1 feed.
/// @param message   The raw JSON payload for this category. This string contains the detailed
///                  data that can be further parsed by the consumer.
/// @param timestamp The server-provided UTC timestamp indicating when the event occurred.
public record LiveTimingMessage(String category, String message, ZonedDateTime timestamp) implements LiveTimingRecord {
}