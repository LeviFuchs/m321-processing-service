package com.example.m321processingservice.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Tolerant Reader: unknown JSON properties from the OrderCreated producer must not fail
 * deserialization, so that additive schema changes on Noah's side (e.g. a new
 * {@code customerReference} field) don't break this service.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer tolerantReaderCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
