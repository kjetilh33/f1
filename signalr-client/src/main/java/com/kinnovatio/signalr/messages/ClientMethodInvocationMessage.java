package com.kinnovatio.signalr.messages;

import java.util.List;

public record ClientMethodInvocationMessage(String messageId, List<String> messageData) implements SignalRMessage {
}
