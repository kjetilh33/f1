package com.kinnovatio.signalr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.List;
import java.util.Map;

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

    public static String toJson(String hub, String method, List<Object> arguments, int identifier) throws JsonProcessingException {
        Map<String, Object> root = Map.of(
            "H", hub,
            "M", method,
            "A", arguments,
            "I", identifier
        );

        return objectMapper.writeValueAsString(root);
    }
}
