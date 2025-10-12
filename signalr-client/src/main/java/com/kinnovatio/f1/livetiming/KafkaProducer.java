package com.kinnovatio.f1.livetiming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.prometheus.metrics.core.metrics.Counter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/// A singleton Kafka producer for publishing Formula 1 live timing data.
///
/// This class is responsible for serializing [com.kinnovatio.signalr.messages.LiveTimingRecord] objects into JSON
/// and sending them to a configured Kafka topic. It reads its configuration (bootstrap servers,
/// topic, client ID) from MicroProfile Config.
///
/// It also maintains a Prometheus counter to track the number of messages sent.
/// Use [#getInstance()] to get the singleton instance.
public class KafkaProducer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Kafka configs. From config file / env variables

    /// The Kafka bootstrap server host and port.
    /// Loaded from the "target.kafka.bootstrapHost" configuration property.
    private static final String kafkaBootstrapHost =
            ConfigProvider.getConfig().getValue("target.kafka.bootstrapHost", String.class);
    /// The Kafka topic to which messages will be published.
    /// Loaded from the "target.kafka.topic" configuration property.
    private static final String kafkaTopic =
            ConfigProvider.getConfig().getValue("target.kafka.topic", String.class);
    /// The client ID for the Kafka producer.
    /// Loaded from the "target.kafka.clientId" configuration property.
    private static final String kafkaClientId =
            ConfigProvider.getConfig().getValue("target.kafka.clientId", String.class);

    private static KafkaProducer instance = null;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer = null;

    /// A Prometheus counter to track the total number of messages sent to Kafka,
    /// labeled by message category.
    static final Counter messageSentCounter = Counter.builder()
            .name("livetiming_connector_message_sent_total")
            .help("Total number of messages sent to Kafka")
            .labelNames("category")
            .register();

    /// Private constructor to enforce the singleton pattern.
    /// Initializes the Jackson ObjectMapper with the JavaTimeModule and configures
    /// and creates the underlying Apache Kafka producer instance.
    private KafkaProducer() {
        objectMapper.registerModule(new JavaTimeModule());

        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapHost);
        props.put("client.id", kafkaClientId);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("linger.ms", 5);

        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        LOG.info("Kafka producer initialized for broker: {}", kafkaBootstrapHost);
    }

    /// Gets the singleton instance of the KafkaProducer.
    ///
    /// @return The singleton [KafkaProducer] instance.
    public static KafkaProducer getInstance() {
        if (instance == null) {
            instance = new KafkaProducer();
        }
        return instance;
    }

    /// Serializes and publishes a [LiveTimingMessage] to the configured Kafka topic.
    /// The message's category is used as the Kafka record key.
    /// The message's timestamp and type are added as Kafka headers.
    ///
    /// @param message The [LiveTimingMessage] to publish.
    public void publish(LiveTimingMessage message) {
        List<Header> headers = new ArrayList<>();
        headers.add(new RecordHeader("timestamp", message.timestamp().toString().getBytes()));
        headers.add(new RecordHeader("messageType", "LiveTimingMessage".getBytes()));

        try {
                publish(message.category(), objectMapper.writeValueAsString(message), headers);
            } catch (JsonProcessingException e) {
                LOG.error("Error writing message to kafka: {}", e);
            }
    }

    /// Asynchronously sends a record to a Kafka topic.
    /// This is a private helper method that handles the actual sending logic.
    ///
    /// @param key     The key for the Kafka record.
    /// @param value   The value (payload) for the Kafka record.
    /// @param headers A list of headers to include with the Kafka record.
    private void publish(String key, String value, List<Header> headers) {
        ProducerRecord <String, String> record = new ProducerRecord<>(kafkaTopic, null, key, value, headers);        

        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                LOG.debug("Message sent to Kafka topic {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
                messageSentCounter.labelValues(key).inc();
            } else {
                LOG.error("Failed to send message to Kafka: {}", exception.getMessage());
            }
        });
    }
}
