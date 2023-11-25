package com.kinnovatio.signalr.messages;

public sealed interface SignalRMessage permits KeepAliveMessage, InitMessage, UnknownMessage {
}
