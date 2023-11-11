package com.kinnovatio.signalr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class MessageDecoder {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isInitMessage(String message) throws Exception {
        boolean returnValue = false;
        JsonNode root = objectMapper.readTree(message);
        if (root.path("S").isIntegralNumber() && root.path("S").asInt() == 1) returnValue = true;

        return returnValue;
    }

    public static boolean isKeepAliveMessage(String message) {
        Objects.requireNonNull(message);
        return message.equalsIgnoreCase("{}");
    }
}
