package com.kinnovatio.signalr.messages.transport;

public record HubResponseMessage(String invocationId, String result) implements SignalRMessage {
}
