package com.kinnovatio;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.eclipse.microprofile.reactive.messaging.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

@ApplicationScoped
public class F1KafkaProcessor {

    @Inject
    @OnOverflow(value = OnOverflow.Strategy.DROP)
    @Channel("status-out")
    Emitter<String> statusEmitter;

    @Incoming("f1-live-raw")
    @RunOnVirtualThread
    @Transactional
    public void toStorage(ConsumerRecords<String, String> records) {
        for (ConsumerRecord<String, String> record : records) {
            System.out.printf(">> offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());

            statusEmitter.send(record.value());
        }

        return;
    }
}
