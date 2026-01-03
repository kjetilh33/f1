package com.kinnovatio;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

public class KafkaTestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> env = new HashMap<>();
        Map<String, String> props1 = InMemoryConnector.switchIncomingChannelsToInMemory("f1-live-raw");
        //Map<String, String> props2 = InMemoryConnector.switchOutgoingChannelsToInMemory("beverages");
        env.putAll(props1);
        //env.putAll(props2);
        return env;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
