package com.kinnovatio;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(KafkaTestLifecycleManager.class)
class F1KafkaProcessorTest {

    @Inject
    F1KafkaProcessor application;

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Test
    void test() {
        InMemorySource<String> ordersIn = connector.source("f1-live-raw");

        ordersIn.send(
                """
                        {
                        "category" : "test",
                        "message" : "{\"Test\" : \"Testing the basics\"}"
                        }
                        """
        );

    }
}
