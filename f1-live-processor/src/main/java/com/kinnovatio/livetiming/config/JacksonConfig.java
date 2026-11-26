package com.kinnovatio.livetiming.config;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
public class JacksonConfig implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.defaultMergeable(true);
    }

}
