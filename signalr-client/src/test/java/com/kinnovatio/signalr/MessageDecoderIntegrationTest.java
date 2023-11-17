package com.kinnovatio.signalr;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDecoderIntegrationTest {
    final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Test
    void messageEncoderTest() throws Exception {
        final String hub = "streaming";
        final String method = "subscribe";
        final List<Object> arguments = List.of(List.of("Heartbeat", "CarData.z"));
        final int identifier = 1;

        String json = MessageDecoder.toJson(hub, method, arguments, identifier);
        LOG.info("Returned json: \n {}", json);

    }
    
}
