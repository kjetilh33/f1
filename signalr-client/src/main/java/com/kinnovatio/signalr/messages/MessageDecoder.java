package com.kinnovatio.signalr.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.prometheus.metrics.core.metrics.Counter;
import org.jboss.logging.Logger;

import com.kinnovatio.signalr.messages.transport.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A utility class for decoding and parsing messages from the F1 SignalR hub.
 * <p>
 * This class provides static methods to identify different types of SignalR messages (e.g., init, keep-alive),
 * parse the raw JSON envelopes, and extract the underlying live timing data. It also handles the
 * decompression of gzipped message payloads, which are common in the F1 live timing feed.
 */
public class MessageDecoder {
    private static final Logger LOG = Logger.getLogger(MessageDecoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Metrics fields
    static final Counter deflatedMessageCounter = Counter.builder()
            .name("livetiming_connector_deflated_message_total")
            .help("Total number messages with compressed data received")
            .register();

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
     * Checks if a raw JSON message is a SignalR keep-alive message.
     * <p>
     * A keep-alive message is an empty JSON object: "{}".
     *
     * @param message The Json message to check.
     * @return true if the message is a keep alive message.
     * @throws JsonProcessingException if the input is not a valid json string.
     */
    public static boolean isKeepAliveMessage(String message) throws JsonProcessingException {
        return parseSignalRMessage(message) instanceof KeepAliveMessage;
    }

    /**
     * Constructs a SignalR JSON message for invoking a hub method.
     *
     * @param hub        The name of the target hub (e.g., "Streaming").
     * @param method     The name of the hub method to call (e.g., "Subscribe").
     * @param arguments  The list of arguments to supply to the method.
     * @param identifier A client-defined identifier for the method call.
     * @return A JSON string representing the SignalR message.
     * @throws JsonProcessingException if the arguments cannot be serialized to JSON.
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
     * Parses a raw SignalR message envelope and extracts the list of {@link LiveTimingMessage}s it contains.
     * <p>
     * This method handles different SignalR message structures, such as hub responses and client method invocations,
     * and delegates to helper methods to extract and parse the actual data payloads.
     *
     * @param messageJson The raw SignalR message as a JSON string.
     * @return A list of {@link LiveTimingMessage}s contained in the envelope. The list will be empty if the
     *         message is not a data-carrying message (e.g., keep-alive).
     * @throws JsonProcessingException if the JSON is malformed.
     */
    public static List<? extends LiveTimingRecord> parseLiveTimingMessages(String messageJson) throws JsonProcessingException {
        SignalRMessage signalRMessage = parseSignalRMessage(messageJson);

        return switch (signalRMessage) {
            case UnknownMessage u -> Collections.emptyList();
            case InitMessage i -> Collections.emptyList();
            case KeepAliveMessage k -> Collections.emptyList();
            case GroupMembershipMessage g -> Collections.emptyList();
            case HubResponseMessage h -> parseHubResponseMessageBody(h.result()).stream().toList();
            case ClientMethodInvocationMessage c -> c.messageData().stream()
                    .map(MessageDecoder::parseSingleMethodInvocationMessageBody)
                    .flatMap(Optional::stream)
                    .toList();
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
        if (root.path("R").isObject()) {
            return new HubResponseMessage(
                    root.path("I").asText(""),
                    root.path("R").toString());
        }

        // Client side hub method invocation carries the payload in an "M" property.
        if (root.path("M").isArray()) {
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

    /**
     * Parses a single message body from a stream of live timing messages.
     * This method unpacks the envelope, decompresses the data if necessary, and creates a {@link LiveTimingMessage}.
     *
     * @param messageJson The JSON string for a single message within the "M" array.
     * @return An {@link Optional} containing the parsed {@link LiveTimingMessage}, or empty if parsing fails.
     */
    private static Optional<LiveTimingMessage> parseSingleMethodInvocationMessageBody(String messageJson) {
        Optional<LiveTimingMessage> returnValue = Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(messageJson);
            
            // If the message is a streaming feed, unpack the envelope and extract the message data.
            if (root.path("H").asText().equalsIgnoreCase("Streaming")
                    && root.path("M").asText().equalsIgnoreCase("feed")
                    && root.path("A") instanceof ArrayNode array) {

                // The arguments array has a fixed structure: [Category, Data, Timestamp]
                String category = array.get(0).asText();
                String messageValue = array.get(1).toString();
                ZonedDateTime timeStamp = ZonedDateTime.parse(array.get(2).asText());

                // Check if the message body is compressed
                if (category.endsWith(".z")) {
                    messageValue = inflate(array.get(1).textValue());
                }

                returnValue = Optional.of(new LiveTimingMessage(category, messageValue, timeStamp));
            }
        } catch (Exception e) {
            LOG.warnf("Error while parsing streaming message: %s", e.toString());
        }

        return returnValue;
    }

    /**
     * Parses the body of a hub response message, which can contain multiple data categories.
     *
     * @param messageJson The JSON string from the "R" property of a hub response.
     * @return A list of parsed {@link LiveTimingMessage}s.
     */
    private static Optional<LiveTimingHubResponseMessage> parseHubResponseMessageBody(String messageJson) {
        Optional<LiveTimingHubResponseMessage> returnValue = Optional.empty();

        try {
            JsonNode root = objectMapper.readTree(messageJson);
            List<LiveTimingMessage> LiveTimingMessages = new ArrayList<>();
            ZonedDateTime timeStamp;

            // Check if we have timestamp data in the payload
            if (root.path("ExtrapolatedClock").path("Utc").isTextual()) {
                timeStamp = ZonedDateTime.parse(root.path("ExtrapolatedClock").path("Utc").textValue());
            } else {
                // Set a default timestamp
                timeStamp = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
            }

            // Iterate over all fields in the JSON object (e.g., "CarData.z", "SessionInfo").
            root.properties().forEach(entry -> {
                    try {
                        String messageValue = entry.getValue().toString();
                        // Check if the message body is compressed
                        if (entry.getKey().endsWith(".z")) {
                                messageValue = inflate(entry.getValue().textValue());
                        }
                        LiveTimingMessages.add(new LiveTimingMessage(entry.getKey(), messageValue, timeStamp));

                    } catch (DataFormatException e) {
                        LOG.warnf("Error while deflating data in message with category %s: %s",
                                entry.getKey(),
                                e.toString());
                    }
            }
            );
            returnValue = Optional.of(new LiveTimingHubResponseMessage(LiveTimingMessages, timeStamp));
        } catch (Exception e) {
            LOG.warnf("Error while parsing hub response message: %s", e.toString());
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
        deflatedMessageCounter.inc();

        StringBuilder result = new StringBuilder();
        // Use Inflater with 'nowrap = true' for raw DEFLATE data, which is what F1 uses.
        Inflater inflater = new Inflater(true);
        inflater.setInput(Base64.getDecoder().decode(compressedStringData));

        while (!inflater.finished()) {
            byte[] outputBytes = new byte[1024];
            int resultLength = inflater.inflate(outputBytes);
            if (resultLength == 0) {
                // This can happen if the buffer is full but inflater needs more input,
                // or if the stream is done. The !inflater.finished() check handles the latter.
                break;
            }
            result.append(new String(outputBytes, 0, resultLength, StandardCharsets.UTF_8));
        }
        inflater.end(); // Release resources
        return result.toString();
    }
}
