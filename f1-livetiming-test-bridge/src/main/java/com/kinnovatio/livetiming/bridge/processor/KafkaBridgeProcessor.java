package com.kinnovatio.livetiming.bridge.processor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.livetiming.bridge.GlobalStateManager;
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
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

/// Processor for F1 live timing messages from Kafka.
@ApplicationScoped
public class KafkaBridgeProcessor {
    private static final Logger LOG = Logger.getLogger(KafkaBridgeProcessor.class);

    @Inject
    MeterRegistry registry;

    @Inject
    GlobalStateManager stateManager;

    @Inject
    @Channel("test-f1-raw-out")
    Emitter<Record<String, String>> testChannelEmitter;

    /// Processes a batch of Kafka records.
    ///
    /// @param record The Kafka consumer record.
    /// @throws Exception If an error occurs during database insertion or processing.
    @Incoming("f1-live-raw-in")
    @Retry(delay = 100, maxRetries = 5)
    @RunOnVirtualThread
    public void process(Record<String, String> record) throws Exception {
        LOG.debugf("Livetiming message received on f1-live-raw-in channel. Message key: %s", record.key());

        if (stateManager.isBrideEnabled()) {
            testChannelEmitter.send(record);
        }
    }
}
