package com.kinnovatio.livetiming.processor;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class KafkaLivetimingProcessorTest {
    @Inject
    KafkaLivetimingProcessor processor;


    @Test
    void cleanProperties() throws Exception {
        String input = "{\"Lines\": {\"1\": {\"GapToLeader\": \"+7.431\", \"IntervalToPositionAhead\": {\"Value\": \"+1.923\"}}}}";
        String expectedOutput = "{\"lines\":{\"1\":{\"gapToLeader\":\"+7.431\",\"intervalToPositionAhead\":{\"value\":\"+1.923\"}}}}";

        String output = processor.cleanProperties(input);
        assertEquals(expectedOutput, output);

    }
}