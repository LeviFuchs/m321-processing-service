package com.example.m321processingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-level configuration: simulated processing time and the service name used in logs.
 */
@ConfigurationProperties(prefix = "processing")
public record ProcessingProperties(
        long timeMs,
        String serviceName,
        String failProduct
) {
}
