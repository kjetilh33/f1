package com.kinnovatio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

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

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
public class F1LiveTimingWithBrokerTest {

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Test
    void testProcessor() {
        ProducerTask producerTask =companion.produce(byte[].class).fromRecords(
                new ProducerRecord<>("f1-live-raw", "category",
                        """
                                {
                                "category": "LapCount",
                                "message": "{\"CurrentLap\":58,\"TotalLaps\":58}",
                                "timestamp": 1766379225.760375900,
                                "isStreaming": true
                                }
                                """.getBytes()),
                new ProducerRecord<>("f1-live-raw", "SessionInfo",
                        """
                                {
                                "category": "SessionInfo",
                                "message": "{\"Meeting\":{\"Key\":1276,\"Name\":\"Abu Dhabi Grand Prix\",\"OfficialName\":\"FORMULA 1 ETIHAD AIRWAYS ABU DHABI GRAND PRIX 2025 \",\"Location\":\"Yas Island\",\"Number\":24,\"Country\":{\"Key\":21,\"Code\":\"UAE\",\"Name\":\"United Arab Emirates\"},\"Circuit\":{\"Key\":70,\"ShortName\":\"Yas Marina Circuit\"}},\"SessionStatus\":\"Finalised\",\"ArchiveStatus\":{\"Status\":\"Complete\"},\"Key\":9839,\"Type\":\"Race\",\"Name\":\"Race\",\"StartDate\":\"2025-12-07T17:00:00\",\"EndDate\":\"2025-12-07T19:00:00\",\"GmtOffset\":\"04:00:00\",\"Path\":\"2025/2025-12-07_Abu_Dhabi_Grand_Prix/2025-12-07_Race/\",\"_kf\":true}",
                                "timestamp": 1766379225.760375900,
                                "isStreaming": true
                                }
                                """.getBytes()),
                new ProducerRecord<>("f1-live-raw", "category",
                        """
                                {
                                "category": "TrackStatus",
                                "message": "{\"Status\":\"1\",\"Message\":\"AllClear\",\"_kf\":true}",
                                "timestamp": 1766379225.760375900,
                                "isStreaming": true
                                }
                                """.getBytes()));

        long msgCount = producerTask.awaitCompletion().count();
        Assertions.assertEquals(3, msgCount);


        // Expect that the tested application processes orders from 'orders' topic and write to 'orders-processed' topic

        //ConsumerTask<String, String> orders = companion.consumeStrings().fromTopics("orders-processed", 10);
        //orders.awaitCompletion();
        //assertEquals(10, orders.count());
    }
}
