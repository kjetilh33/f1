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

    /**
     * Check if a message is a SignalR init message.
     *
     * @param message The json message to check.
     * @return true if the message is an init message.
     */
    public static boolean isInitMessage(String message) throws Exception {
        boolean returnValue = false;
        JsonNode root = objectMapper.readTree(message);
        if (root.path("S").isIntegralNumber() && root.path("S").asInt() == 1) returnValue = true;

        return returnValue;
    }

    /**
     * Check if a message is a SignalR keep alive message.
     *
     * @param message The Json message to check.
     * @return true if the message is a keep alive message.
     */
    public static boolean isKeepAliveMessage(String message) {
        Objects.requireNonNull(message);
        return message.equalsIgnoreCase("{}");
    }

    /**
     * Build a SingnalR json message based on standard inputs.
     *
     * @param hub The target hub.
     * @param method The hub method to call.
     * @param arguments The arguments to supply to the method.
     * @param identifier An identifier for the method call.
     * @return The Json message representation of SignalR message.
     * @throws JsonProcessingException
     */
    public static String toMessageJson(String hub, String method, List<Object> arguments, int identifier) throws JsonProcessingException {
        Map<String, Object> root = Map.of(
            "H", hub,
            "M", method,
            "A", arguments,
            "I", identifier
        );

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Decompresses a base64 encoded gzip byte stream to String format.
     *
     * @param compressedStringData A gzip compressed and base64 encoded string.
     * @return The decompressed string.
     * @throws DataFormatException if the compressed data format is invalid.
     */
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
