package com.kinnovatio.signalr.messages;

public sealed interface SignalRMessage permits KeepAliveMessage, InitMessage, GroupMembershipMessage,
        HubResponseMessage, ClientMethodInvocationMessage, UnknownMessage {
}
