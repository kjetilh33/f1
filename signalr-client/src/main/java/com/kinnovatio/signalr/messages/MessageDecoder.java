package com.kinnovatio.signalr.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class MessageDecoder {
    private static final Logger LOG = Logger.getLogger(MessageDecoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Check if a message is a SignalR init message.
     *
     * @param message The json message to check.
     * @return true if the message is an init message.
     * @throws JsonProcessingException if the input is not a valid json string.
     */
    public static boolean isInitMessage(String message) throws JsonProcessingException {
        return parseSignalRMessage(message) instanceof InitMessage;
    }

    /**
     * Check if a message is a SignalR keep alive message.
     *
     * @param message The Json message to check.
     * @return true if the message is a keep alive message.
     * @throws JsonProcessingException if the input is not a valid json string.
     */
    public static boolean isKeepAliveMessage(String message) throws JsonProcessingException {
        return parseSignalRMessage(message) instanceof KeepAliveMessage;
    }

    /**
     * Build a SingnalR json message based on standard inputs.
     *
     * @param hub The target hub.
     * @param method The hub method to call.
     * @param arguments The arguments to supply to the method.
     * @param identifier An identifier for the method call.
     * @return The Json message representation of SignalR message.
     * @throws JsonProcessingException if the input is not a valid json string.
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
     * Parse a SignalR message envelope and extract the contents.
     *
     * @param messageJson The SignalR message envelope (the raw message)
     * @return a list of messages contained in the envelope.
     * @throws JsonProcessingException if the json is malformed
     */
    public static List<LiveTimingMessage> parseLiveTimingMessages(String messageJson) throws JsonProcessingException {
        SignalRMessage signalRMessage = parseSignalRMessage(messageJson);

        return switch (signalRMessage) {
            case UnknownMessage u -> Collections.emptyList();
            case InitMessage i -> Collections.emptyList();
            case KeepAliveMessage k -> Collections.emptyList();
            case GroupMembershipMessage g -> Collections.emptyList();
            case HubResponseMessage h -> {
                List<LiveTimingMessage> returnList = new ArrayList<>();
                JsonNode root = objectMapper.readTree(h.result());
                ZonedDateTime timeStamp;

                // Check if we have timestamp data in the payload
                if (root.path("ExtrapolatedClock").path("Utc").isTextual()) {
                    timeStamp = ZonedDateTime.parse(root.path("ExtrapolatedClock").path("Utc").textValue());
                } else {
                    // If no timestamp in the payload, set a default
                    timeStamp = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
                }

                root.fields().forEachRemaining(entry ->
                        returnList.add(new LiveTimingMessage(entry.getKey(), entry.getValue().toString(), timeStamp)));

                yield returnList;
            }
            case ClientMethodInvocationMessage c -> {
                yield c.messageData().stream()
                        .map(MessageDecoder::parseSingleMethodInvocationMessage)
                        .flatMap(Optional::stream)
                        .toList();
            }
        };

    }

    /**
     * Parse a raw SignalR message into one of its basic message types. The parsed message can then be interrogated
     * further for content.
     *
     * The returned message can be interrogated by using pattern matching to test the "type" of message.
     *
     * @param messageJson the raw SignalR message in Json format.
     * @return the message parsed into one of the basic {@code SignalRMessage} types.
     * @throws JsonProcessingException if the input is not a valid json string.
     */
    public static SignalRMessage parseSignalRMessage(String messageJson) throws JsonProcessingException {
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
                    parseJsonObjectArray(root.path("M")));
        }

        // Group membership messages contains the "G" property
        if (root.path("G").isTextual()) {
            return new GroupMembershipMessage(
                    root.path("C").asText(""),
                    root.path("G").asText(""),
                    parseJsonObjectArray(root.path("M")));
        }

        // Hub reply messages (replies to client calling the hub) contains the "R" property
        if (!root.path("R").isObject()) {
            return new HubResponseMessage(
                    root.path("I").asText(""),
                    root.path("R").toString());
        }

        // Client side hub method invocation carries the payload in an "M" property.
        if (!root.path("M").isArray()) {
            return new ClientMethodInvocationMessage(
                    root.path("C").asText(""),
                    parseJsonObjectArray(root.path("M")));
        }

        // If we don't have a match with any of the known types, return the raw input as an unknown message type
        return new UnknownMessage(messageJson);
    }

    private static List<String> parseJsonObjectArray(JsonNode node) {
        List<String> objects = new ArrayList<>();

        if (node instanceof ArrayNode array) {
            for (JsonNode n : array) {
                if (n.isObject()) objects.add(n.toString());
            }
        }
        return objects;
    }

    /*

     */
    private static Optional<LiveTimingMessage> parseSingleMethodInvocationMessage(String messageJson) {
        Optional<LiveTimingMessage> returnValue = Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(messageJson);
            
            // If the message is a streaming feed, unpack the envelope and extract the message data.
            if (root.path("H").asText().equalsIgnoreCase("Streaming")
                    && root.path("M").asText().equalsIgnoreCase("feed")
                    && root.path("A") instanceof ArrayNode array) {
                String category = array.get(0).asText();
                String messageValue = array.get(1).toString();
                ZonedDateTime timeStamp = ZonedDateTime.parse(array.get(2).asText());

                // Check if the message body is compressed
                if (category.endsWith(".z")) {
                    messageValue = inflate(messageValue);
                }

                returnValue = Optional.of(new LiveTimingMessage(category, messageValue, timeStamp));
            }
        } catch (Exception e) {
            LOG.warnf("Error while parsing streaming message: %s", e.toString());
        }

        return returnValue;
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
