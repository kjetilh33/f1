package com.kinnovatio.signalr.messages;

import java.util.List;

public record GroupMembershipMessage(String messageId, String groupToken, List<String> messageData) implements SignalRMessage {
}
