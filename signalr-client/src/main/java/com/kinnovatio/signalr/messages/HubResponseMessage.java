package com.kinnovatio.signalr.messages;

public record HubResponseMessage(String invocationId, String result) implements SignalRMessage {
}
