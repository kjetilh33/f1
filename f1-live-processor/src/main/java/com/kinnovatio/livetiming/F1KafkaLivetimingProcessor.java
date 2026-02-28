package com.kinnovatio.livetiming;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import java.util.Set;

/// Processor for F1 live timing messages from Kafka.
@ApplicationScoped
public class F1KafkaLivetimingProcessor {
    private static final Logger LOG = Logger.getLogger(F1KafkaLivetimingProcessor.class);

    private static final Set<String> excludeCategories = Set.of("Heartbeat");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;


    @Inject
    @Channel("livetiming-out")
    Emitter<Record<String, String>> livetimingOutEmitter;

    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("track-status")
    Emitter<String> trackStatusEmitter;

    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("session-info")
    Emitter<String> sessionInfoEmitter;

    /// Initializes the processor on startup.
    /// This method is triggered by the `StartupEvent`. It logs the startup configuration.
    ///
    /// @param ev The startup event.
    public void onStartup(@Observes StartupEvent ev) {
        LOG.infof("Starting the live timing message processor...");

        LOG.infof("The message processor is ready. Waiting for live timing messages...");
    }



    /// Processes a batch of Kafka records.
    ///
    /// @param record The Kafka consumer record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-in")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    public void process(Record<String, String> record) throws Exception {
        LOG.infof("Livetiming message received on f1-live-raw-in channel. Message key: %s", record.key());
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
            } else {
                // The message is ok to distribute to the generic downstream.
                livetimingOutEmitter.send(record);
                LOG.infof("Livetiming message publisched to livetiming-out channel. Message category: %s", message.category());
                // Route the message to appropriate per-category handlers
                switch (message.category()) {
                    case "TrackStatus" -> trackStatusEmitter.send(record.value());
                    case "SessionInfo" -> sessionInfoEmitter.send(record.value());
                    default -> {
                        LOG.debugf("Message router: unknown message category: %s", message.category());
                    }

                }
            }

        } catch (JsonParseException e) {
            LOG.warnf("Failed parsing livetiming record: %s. Record content: %s", e, record.value());
        }
    }
}
