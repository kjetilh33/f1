package com.kinnovatio.f1.livetiming;

import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.time.Instant;
import java.util.List;

/// Represents the overall status and recent metrics snapshot of the connector.
///
/// This record holds information about the current connector operational state,
/// the timestamp of the last session evaluation, a recent queue of received messages,
/// and message ingestion rates aggregated per second and per minute.
///
/// @param connectorState The textual representation of the connector state (e.g., from [Client.State]).
/// @param lastSessionCheck The timestamp when the F1 session state was last checked.
/// @param messages A list of recent [LiveTimingMessage]s captured from the queue.
/// @param messageRatePerSecond The list of [RateTuple]s representing message throughput per second.
/// @param messageRatePerMinute The list of [RateTuple]s representing message throughput per minute.
public record ConnectorStatus(String connectorState, Instant lastSessionCheck, List<LiveTimingMessage> messages,
                              List<RateTuple> messageRatePerSecond, List<RateTuple> messageRatePerMinute) {
}
