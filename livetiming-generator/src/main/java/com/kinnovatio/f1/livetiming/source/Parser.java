package com.kinnovatio.f1.livetiming.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import com.kinnovatio.signalr.messages.MessageDecoder;
import com.kinnovatio.signalr.messages.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class Parser {
    private static final Logger LOG = LoggerFactory.getLogger(Parser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<? extends LiveTimingRecord> parseSignalRMessage(String rawMessage) {
        String loggingPrefix = "processMessage() - ";
        List<? extends LiveTimingRecord> messages = Collections.emptyList();

        // Capture statistics on the number of messages received
        String recordCategory = "Unknown";
        try {
            recordCategory = switch (MessageDecoder.parseSignalRMessage(rawMessage)) {
                case UnknownMessage u -> "Unknown";
                case InitMessage i -> "Init";
                case KeepAliveMessage k -> "KeepAlive";
                case GroupMembershipMessage g -> "GroupMembership";
                case HubResponseMessage h -> "HubResponse";
                case ClientMethodInvocationMessage c -> "ClientMethodInvocation";
            };

            messages = MessageDecoder.parseLiveTimingMessages(rawMessage);
            LOG.debug(loggingPrefix + "Parsed raw message into {} live timing messages", messages.size());

        } catch (JsonProcessingException e) {
            LOG.warn(loggingPrefix + "Error when processing received signalR message: Raw message: '{}'. Error: {}",
                    rawMessage, e.getMessage());;
        }

        return messages;
    }
}
