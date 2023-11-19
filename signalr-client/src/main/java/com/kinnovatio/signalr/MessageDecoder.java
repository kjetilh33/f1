package com.kinnovatio.signalr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

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

    public static String toMessageJson(String hub, String method, List<Object> arguments, int identifier) throws JsonProcessingException {
        Map<String, Object> root = Map.of(
            "H", hub,
            "M", method,
            "A", arguments,
            "I", identifier
        );

        return objectMapper.writeValueAsString(root);
    }

    public static String inflate(String compressedStringData) throws DataFormatException {
        StringBuilder result = new StringBuilder();
        Inflater inflater = new Inflater(true);
        inflater.setInput(Base64.getDecoder().decode(compressedStringData));

        while (!inflater.finished()) {
            byte[] outputBytes = new byte[1024];
            int resultLenght = inflater.inflate(outputBytes);
            result.append(new String(outputBytes, 0, resultLenght, StandardCharsets.UTF_8));
        }

        return result.toString();
    }
}
