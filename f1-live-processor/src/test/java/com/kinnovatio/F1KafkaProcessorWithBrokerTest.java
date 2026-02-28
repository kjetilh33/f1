package com.kinnovatio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import io.smallrye.reactive.messaging.kafka.companion.ProducerTask;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
public class F1KafkaProcessorWithBrokerTest {

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testProcessor() throws Exception {

        ProducerTask producerTask =companion.produceStrings().fromRecords(
                new ProducerRecord<>("test-f1-live-raw", "SessionInfo",
                        objectMapper.writeValueAsString(
                                new LiveTimingMessage("SessionInfo", "{\"CurrentLap\":58,\"TotalLaps\":58}",
                                        Instant.now().atZone(ZoneId.of("UTC")), true)
                        )
                ),
                new ProducerRecord<>("test-f1-live-raw", "SessionInfo",
                        objectMapper.writeValueAsString(
                                new LiveTimingMessage("SessionInfo", "{\"Meeting\":{\"Key\":1276,\"Name\":\"Abu Dhabi Grand Prix\",\"OfficialName\":\"FORMULA 1 ETIHAD AIRWAYS ABU DHABI GRAND PRIX 2025 \",\"Location\":\"Yas Island\",\"Number\":24,\"Country\":{\"Key\":21,\"Code\":\"UAE\",\"Name\":\"United Arab Emirates\"},\"Circuit\":{\"Key\":70,\"ShortName\":\"Yas Marina Circuit\"}},\"SessionStatus\":\"Finalised\",\"ArchiveStatus\":{\"Status\":\"Complete\"},\"Key\":9839,\"Type\":\"Race\",\"Name\":\"Race\",\"StartDate\":\"2025-12-07T17:00:00\",\"EndDate\":\"2025-12-07T19:00:00\",\"GmtOffset\":\"04:00:00\",\"Path\":\"2025/2025-12-07_Abu_Dhabi_Grand_Prix/2025-12-07_Race/\",\"_kf\":true}",
                                        Instant.now().atZone(ZoneId.of("UTC")), true)
                        )
                ),
                new ProducerRecord<>("test-f1-live-raw", "TrackStatus",
                        objectMapper.writeValueAsString(
                                new LiveTimingMessage("TrackStatus", "{\"Status\":\"1\",\"Message\":\"AllClear\",\"_kf\":true}",
                                        Instant.now().atZone(ZoneId.of("UTC")), true)
                        )
                )
        );

        long msgCount = producerTask.awaitCompletion().count();
        IO.println(String.format("Published %d messages.", msgCount));
        Assertions.assertEquals(3, msgCount);


        // Expect that the tested application processes orders from 'orders' topic and write to 'orders-processed' topic

        ConsumerTask<String, String> processed = companion.consumeStrings().fromTopics("test-f1-live-processed", 3);
        processed.awaitCompletion(Duration.ofSeconds(15));
        IO.println(String.format("Received %d messages.", processed.count()));
        assertEquals(3, processed.count());
    }
}
