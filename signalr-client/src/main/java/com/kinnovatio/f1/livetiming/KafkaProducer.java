package com.kinnovatio.f1.livetiming;

import io.prometheus.metrics.core.metrics.Counter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class KafkaProducer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);

    // Kafka configs. From config file / env variables

    private static final String kafkaBootstrapHost =
            ConfigProvider.getConfig().getValue("target.kafka.bootstrapHost", String.class);
    private static final String kafkaTopic =
            ConfigProvider.getConfig().getValue("target.kafka.topic", String.class);
    private static final String kafkaClientId =
            ConfigProvider.getConfig().getValue("target.kafka.clientId", String.class);

    private static KafkaProducer instance = null;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer = null;

    static final Counter messageSentCounter = Counter.builder()
            .name("livetiming_connector_message_sent_total")
            .help("Total number of messages sent to Kafka")
            .labelNames("category")
            .register();


    private KafkaProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapHost);
        props.put("client.id", kafkaClientId);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", 1);
        props.put("linger.ms", 5);

        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        LOG.info("Kafka producer initialized for broker: {}", kafkaBootstrapHost);
    }

    public static KafkaProducer getInstance() {
        if (instance == null) {
            instance = new KafkaProducer();
        }
        return instance;
    }

    public void publish(String key, String value) {
        producer.send(new ProducerRecord<>(kafkaTopic, key, value), (metadata, exception) -> {
            if (exception == null) {
                LOG.debug("Message sent to Kafka topic {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
                messageSentCounter.labelValues(key).inc();
            } else {
                LOG.error("Failed to send message to Kafka: {}", exception.getMessage());
            }
        });
    }
}
