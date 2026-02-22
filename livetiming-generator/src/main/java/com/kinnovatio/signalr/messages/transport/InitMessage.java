package com.kinnovatio.signalr.messages.transport;

import java.util.List;

public record InitMessage(String messageId, int init, List<String> messageData) implements SignalRMessage {
}
