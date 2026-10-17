package com.kinnovatio.signalr.messages;

import java.time.Instant;
import java.util.List;

/// Represents a response message received from an F1 live timing SignalR hub method invocation.
///
/// A hub response typically contains an initial snapshot across multiple data categories
/// (e.g., after subscribing to live timing streams) along with a timestamp.
///
/// @param messages The list of parsed [LiveTimingMessage] instances extracted from the hub response.
/// @param timestamp The timestamp associated with the hub response message.
public record LiveTimingHubResponseMessage(List<LiveTimingMessage> messages, Instant timestamp) implements LiveTimingRecord {

}
