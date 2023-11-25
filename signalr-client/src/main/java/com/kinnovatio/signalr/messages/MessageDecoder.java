package com.kinnovatio.signalr.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
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

    public SignalRMessage parseSignalRMessage(String messageJson) throws JsonProcessingException {
        Objects.requireNonNull(messageJson);

        // Let's just shortcut if it is a keep alive message
        if (messageJson.equalsIgnoreCase("{}")) return new KeepAliveMessage();

        // Checking the other message types. We need to inspect the Json contents
        JsonNode root = objectMapper.readTree(messageJson);

        // Init messages contains "S" property
        if (root.path("S").isIntegralNumber() && root.path("S").asInt() == 1) {
            return new InitMessage(
                    root.path("C").asText(""),
                    root.path("S").asInt(),
                    parserJsonObjectArray(root.path("M")));
        }

        // Hub reply messages (replies to client calling the hub) contains the "R" property


        // If we don't have a match with any of the known types, return the raw input as an unknown message type
        return new UnknownMessage(messageJson);
    }

    private List<String> parserJsonObjectArray(JsonNode node) {
        List<String> objects = new ArrayList<>();

        if (node instanceof ArrayNode array) {
            for (JsonNode n : array) {
                if (n.isObject()) objects.add(n.toString());
            }
        }
        return objects;
    }

    /**
     * Parse a SignalR message envelope and extract the contents.
     *
     * @param messageJson The SignalR message envelope (the raw message)
     * @return a list of messages contained in the envelope.
     * @throws JsonProcessingException if the json is malformed
     */
    public static List<Message> parseMessages(String messageJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(messageJson);

        if (root.path("R").isObject()) {
            return Collections.emptyList();
        } else if (root.path("C").isTextual() && root.path("M").isArray()) {
            return Collections.emptyList();
        } else {
            return Collections.emptyList();
        }
    }

    private Message parseSingleMessage(String messageJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(messageJson);

        return new Message("test", "my message", ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")));
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
            int resultLength = inflater.inflate(outputBytes);
            result.append(new String(outputBytes, 0, resultLength, StandardCharsets.UTF_8));
        }

        return result.toString();
    }
}
