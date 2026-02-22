package com.kinnovatio.signalr.messages.transport;

import java.util.List;

public record GroupMembershipMessage(String messageId, String groupToken, List<String> messageData) implements SignalRMessage {
}
