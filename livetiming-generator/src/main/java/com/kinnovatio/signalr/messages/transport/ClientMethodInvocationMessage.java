package com.kinnovatio.signalr.messages.transport;

import java.util.List;

public record ClientMethodInvocationMessage(String messageId, List<String> messageData) implements SignalRMessage {
}
