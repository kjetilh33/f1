package com.kinnovatio.f1.config;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
public class JacksonConfig implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        builder.defaultMergeable(true);
        //builder.propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
    }

}
