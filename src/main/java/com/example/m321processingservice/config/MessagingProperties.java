package com.example.m321processingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of the shared RabbitMQ topology (exchange, routing keys, queue).
 * <p>
 * These values come from the shared AsyncAPI contract between Noah's Order Service and this
 * Processing Service. Until a real shared-contracts repository / a {@code LEVI HANDOFF} section
 * provides authoritative names, the defaults below are this repository's local placeholder —
 * see {@code docs/asyncapi.yaml} and the README section "Contract Status".
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String exchange,
        String orderCreatedRoutingKey,
        String orderProcessedRoutingKey,
        String orderCreatedQueue
) {
}
