package com.kinnovatio.livetiming.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TimingStatsDataProcessorTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void jsonMerge() throws Exception {
        String baselineJson = """
                {
                    "lines": {
                        "1": {
                            "gapToLeader": "+2",
                            "intervalToPositionAhead": "+1"
                        }
                    }
                }
                """;
        String updateJson = """
                {
                    "lines": {
                        "1": {
                            "gapToLeader": "+3"
                        },
                        "2": {
                            "gapToLeader": "+10",
                            "intervalToPositionAhead": "+2"
                        }
                    }
                }
                """;

        JsonNode baseline = objectMapper.readTree(baselineJson);
        JsonNode update = objectMapper.readTree(updateJson);

        ObjectNode updated = objectMapper.readerForUpdating(baseline).readValue(update);
        IO.println(objectMapper.writeValueAsString(updated));
    }

}