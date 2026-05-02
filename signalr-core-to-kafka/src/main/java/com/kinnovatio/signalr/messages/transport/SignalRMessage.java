package com.kinnovatio.signalr.messages.transport;

public sealed interface SignalRMessage permits KeepAliveMessage, InitMessage, GroupMembershipMessage,
        HubResponseMessage, ClientMethodInvocationMessage, UnknownMessage {
}
