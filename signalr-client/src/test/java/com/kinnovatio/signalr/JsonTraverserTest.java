package com.kinnovatio.signalr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinnovatio.signalr.messages.MessageDecoder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JsonTraverserTest {
    final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Test
    void jsonObjectTraverserTest() throws Exception {
        String jsonString = """
                {
                    "object": {
                        "1": "my first key",
                        "2": "my second key"
                        }
                }
                """;

        JsonNode objectNode = new ObjectMapper().readTree(jsonString)
                .path("object");

        System.out.println("Iterating the object node: ");
        for (JsonNode item : objectNode) {
            LOG.info("Object node item: {}", item.toString());
        }
    }

    @Test
    void jsonArrayTraverserTest() throws Exception {
        String jsonString = """
                {
                    "array": [
                        {
                        "1": "my first item"
                        },
                        {
                        "2": "my second item"
                        }
                    ]
                }
                """;

        JsonNode objectNode = new ObjectMapper().readTree(jsonString)
                .path("array");

        System.out.println("Iterating the array node: ");
        for (JsonNode item : objectNode) {
            LOG.info("Array item: {}", item.toString());
        }
    }
    
}
