package com.kinnovatio.signalr.messages.transport;

public record UnknownMessage(String rawMessage) implements SignalRMessage {
}
