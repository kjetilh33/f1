package com.kinnovatio.signalr.messages;

/// A sealed interface representing a record received from the Formula 1 live timing SignalR feed.
///
/// Permitted implementations are:
/// - [LiveTimingMessage]: A single live timing data event.
/// - [LiveTimingHubResponseMessage]: A composite response message received from a hub invocation.
public sealed interface LiveTimingRecord permits LiveTimingHubResponseMessage, LiveTimingMessage {
}
