package com.kinnovatio.signalr.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.prometheus.metrics.core.metrics.Counter;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/// A utility class for decoding and parsing messages from the F1 SignalR hub.
///
/// This class provides static methods to identify different types of SignalR messages (e.g., init, keep-alive),
/// parse the raw JSON envelopes, and extract the underlying live timing data. It also handles the
/// decompression of gzipped message payloads, which are common in the F1 live timing feed.
public class MessageDecoder {
    private static final Logger LOG = Logger.getLogger(MessageDecoder.class);

    // Metrics fields
    static final Counter deflatedMessageCounter = Counter.builder()
            .name("livetiming_connector_deflated_message_total")
            .help("Total number messages with compressed data received")
            .register();

    /// Parses a raw SignalR message envelope and extracts the list of [LiveTimingMessage]s it contains.
    ///
    /// This method handles different SignalR message structures, such as hub responses and client method invocations,
    /// and delegates to helper methods to extract and parse the actual data payloads.
    ///
    /// @param messageJson The raw SignalR message as a JSON string.
    /// @return A list of [LiveTimingMessage]s contained in the envelope. The list will be empty if the
    ///         message is not a data-carrying message (e.g., keep-alive).
    public static List<? extends LiveTimingRecord> parseLiveTimingMessages(JsonElement messageJson) {
        if (messageJson == null || messageJson.isJsonNull()) {
            LOG.warnf("parseLiveTimingMessages() - Received a null object instead of the expected valid Json element");
            return Collections.emptyList();
        }

        if (messageJson.isJsonObject()) {
            return parseHubResponseMessage(messageJson).stream().toList();
        } else {
            return Collections.emptyList();
        }
    }

    /// Parses a single message body from a stream of live timing messages.
    /// This method unpacks the envelope, decompresses the data if necessary, and creates a [LiveTimingMessage].
    ///
    /// @param messageJson The JSON string for a single message within the "M" array.
    /// @return An [Optional] containing the parsed [LiveTimingMessage], or empty if parsing fails.
    public static Optional<LiveTimingMessage> parseMessageFeed(JsonElement categoryJson, JsonElement messageJson, JsonElement timeStampJson) {
        String category = "";
        String messageValue = "";
        Instant timeStamp = Instant.now();

        // Check input data types
        if (categoryJson == null || categoryJson.isJsonNull()) {
            LOG.warnf("parseMessageFeed() - Received a null object instead of the expected valid message category");
            return Optional.empty();
        }
        if (messageJson == null || messageJson.isJsonNull()) {
            LOG.warnf("parseMessageFeed() - Received a null object instead of the expected valid message");
            return Optional.empty();
        }

        // Start parsing
        if (categoryJson.isJsonPrimitive()) {
            category = categoryJson.getAsString();
        } else {
            LOG.warnf("parseMessageFeed() - The message category is not the expected string. Will skip parsing it. Received data: %s", categoryJson.toString());
        }

        if (messageJson.isJsonObject()) {
            messageValue = messageJson.toString();
        } else {
            LOG.warnf("parseMessageFeed() - The message is not the expected Json object. Will skip parsing it. Received data: %s", messageJson.toString());
        }

        if (timeStampJson.isJsonPrimitive()) {
            try {
                timeStamp = Instant.parse(timeStampJson.getAsString());
            } catch (DateTimeParseException e) {
                LOG.warnf("parseMessageFeed() - The message timestamp is not in a valid format: %s. Will use current clock time. Message category: %s. Message summary: %s",
                        timeStampJson.getAsString(),
                        category,
                        messageJson.toString().substring(0, Math.min(400, messageJson.toString().length())));
            }
        } else {
            LOG.warnf("parseMessageFeed() - The timestamp is not the expected string. Will skip parsing it. Received data: %s", timeStampJson.toString());
        }

        // Check if the message body is compressed
        if (category.endsWith(".z")) {
            try {
                messageValue = inflate(messageValue);
            } catch (DataFormatException ex) {
                LOG.warnf("Error while deflating data in message with category %s. Error message: %s", category, ex.toString());
            }
        }

        return Optional.of(new LiveTimingMessage(category, messageValue, timeStamp, true));
    }

    /// Parses the body of a hub response message, which can contain multiple data categories.
    ///
    /// @param root The JSON root element of a hub response.
    /// @return A list of parsed [LiveTimingMessage]s.
    public static Optional<LiveTimingHubResponseMessage> parseHubResponseMessage(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            LOG.warnf("parseHubResponseMesasge() - Received a null object instead of the expected valid Json element");
            return Optional.empty();
        }
        Optional<LiveTimingHubResponseMessage> returnValue = Optional.empty();

        if (root.isJsonObject()) {
            List<LiveTimingMessage> LiveTimingMessages = new ArrayList<>();
            Instant timeStamp = Instant.now();
            JsonObject objectRoot = root.getAsJsonObject();

            // Check if we have timestamp data in the payload
            if (objectRoot.has("ExtrapolatedClock") && objectRoot.get("ExtrapolatedClock").isJsonObject()
                    && objectRoot.get("ExtrapolatedClock").getAsJsonObject().has("Utc")) {
                try {
                    timeStamp = Instant.parse(objectRoot.get("ExtrapolatedClock").getAsJsonObject().get("Utc").getAsString());
                } catch (DateTimeParseException e) {
                    LOG.warnf("parseHubResponseMessage() - The message timestamp is not in a valid format: %s. Will use current clock time.",
                            objectRoot.get("ExtrapolatedClock").getAsJsonObject().get("Utc").getAsString());
                }
            } else {
                LOG.warnf("parseHubResponseMessage() - Could not find a valid timestamp. Will use current clock time.");
            }

            // Iterate over all fields in the JSON object (e.g., "CarData.z", "SessionInfo").
            Instant finalTimeStamp = timeStamp; // must do this stupid copy to satisfy "effectively final" requirement
            objectRoot.entrySet().forEach(entry -> {
                String messageValue = entry.getValue().toString();
                // Check if the message body is compressed
                if (entry.getKey().endsWith(".z")) {
                    try {
                        messageValue = inflate(entry.getValue().getAsString());
                    } catch (DataFormatException e) {
                        LOG.warnf("Error while deflating data in message with category %s: %s",
                                entry.getKey(),
                                e.toString());
                    }
                }

                LiveTimingMessages.add(new LiveTimingMessage(entry.getKey(), messageValue, finalTimeStamp, false));
            });
            returnValue = Optional.of(new LiveTimingHubResponseMessage(LiveTimingMessages, timeStamp));
        } else {
            LOG.warnf("parseHubResponseMessage() - The received hub response is not an expected Json object. Will skip parsing it.");
        }

        return returnValue;
    }

    /// Decompresses a base64 encoded gzip byte stream to String format.
    ///
    /// @param compressedStringData A gzip compressed and base64 encoded string.
    /// @return The decompressed string.
    /// @throws DataFormatException if the compressed data format is invalid.
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
