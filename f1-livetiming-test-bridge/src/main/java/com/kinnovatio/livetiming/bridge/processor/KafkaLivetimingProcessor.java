package com.kinnovatio.livetiming.bridge.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// Processor for F1 live timing messages from Kafka.
@ApplicationScoped
public class KafkaLivetimingProcessor {
    private static final Logger LOG = Logger.getLogger(KafkaLivetimingProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    @Channel("livetiming-out")
    Emitter<Record<String, String>> livetimingOutEmitter;


    /// Processes a batch of Kafka records.
    ///
    /// @param record The Kafka consumer record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-in")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    public void process(Record<String, String> record) throws Exception {
        LOG.debugf("Livetiming message received on f1-live-raw-in channel. Message key: %s", record.key());


        try {
            LiveTimingMessage message = objectMapper.readValue(record.value(), LiveTimingMessage.class);

            if (message.message().isEmpty() || excludeCategories.contains(message.category())) {
                // The message should be discarded and not processed further.
                Counter.builder("livetiming_router_processor_record_discarded_total")
                        .description("Total number of live timing records discarded by the router.")
                        .tag("category" , message.category())
                        .register(registry)
                        .increment();

                LOG.debugf("Discarded record >> offset = %d, key = %s, value = %s%n", record.key(), record.value());
                return;
            }

            if (message.isStreaming()) {
                // The message should be forwarded to the live-streaming channel.
                livetimingOutEmitter.send(processedRecord);
                LOG.tracef("Livetiming message published to the livetiming-out channel. Message category: %s", message.category());
            }



        } catch (JsonParseException e) {
            LOG.warnf("Failed parsing livetiming record: %s. Record content: %s", e, record.value());
        }
    }
}
