package com.kinnovatio.signalr.messages;

import java.util.List;

public record InitMessage(String messageId, int init, List<String> messageData) implements SignalRMessage {
}
