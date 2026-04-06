package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.GlobalStateManager;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
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
public class KafkaLivetimingProcessor {
    private static final Logger LOG = Logger.getLogger(KafkaLivetimingProcessor.class);

    private static final Set<String> excludeCategories = Set.of("Heartbeat");
    private static final Set<String> nonStreamingCategories = Set.of("SessionInfo", "DriverList", "TimingData");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    @Channel("livetiming-out")
    Emitter<Record<String, String>> livetimingOutEmitter;

    @Inject
    @Channel("track-status")
    Emitter<String> trackStatusEmitter;

    @Inject
    @Channel("session-info")
    Emitter<String> sessionInfoEmitter;

    @Inject    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("race-control-message")
    Emitter<String> raceControlMessageEmitter;

    @Inject
    @Channel("weather-data")
    Emitter<String> weatherDataEmitter;

    @Inject
    @Channel("driver-list")
    Emitter<String> driverListEmitter;

    @Inject
    @Channel("timing-data")
    Emitter<String> timingDataEmitter;

    /*
    @Inject
    @Channel("timing-app-data")
    Emitter<String> timingAppDataEmitter;

    @Inject
    @Channel("timing-stats")
    Emitter<String> timingStatsEmitter;

     */

    /// Processes a batch of Kafka records.
    ///
    /// @param record The Kafka consumer record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-in")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    public void process(Record<String, String> record) throws Exception {
        LOG.debugf("Livetiming message received on f1-live-raw-in channel. Message key: %s", record.key());
        stateManager.registerMessageReceived();

        try {
            LiveTimingMessage message = objectMapper.readValue(record.value(), LiveTimingMessage.class);

            if (message.message().isEmpty()
                    || excludeCategories.contains(message.category())
                    || (!message.isStreaming() && !nonStreamingCategories.contains(message.category()))) {
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
                LOG.tracef("Livetiming message publisched to livetiming-out channel. Message category: %s", message.category());

                // Route the message to appropriate per-category handlers
                switch (message.category()) {
                    case "TrackStatus" -> trackStatusEmitter.send(record.value());
                    case "SessionInfo" -> sessionInfoEmitter.send(record.value());
                    case "RaceControlMessages" -> raceControlMessageEmitter.send(record.value());
                    case "WeatherData" -> weatherDataEmitter.send(record.value());
                    case "DriverList" -> driverListEmitter.send(record.value());
                    case "TimingData" -> timingDataEmitter.send(record.value());
                    //case "SessionData" -> sessionDataEmitter.send(record.value());
                    //case "TimingAppData" -> timingAppDataEmitter.send(record.value());
                    //case "TimingStats" -> timingStatsEmitter.send(record.value());
                    default -> {
                        LOG.debugf("Message router: unknown message category received: %s", message.category());
                    }
                }
            }

        } catch (JsonParseException e) {
            LOG.warnf("Failed parsing livetiming record: %s. Record content: %s", e, record.value());
        }
    }
}
