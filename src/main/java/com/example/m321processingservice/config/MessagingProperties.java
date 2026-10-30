package com.example.m321processingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of the shared RabbitMQ topology (exchange, routing keys, queue).
 * <p>
 * These values mirror the authoritative Order Service AsyncAPI contract documented in
 * {@code docs/asyncapi.yaml}.
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String exchange,
        String orderCreatedRoutingKey,
        String orderProcessedRoutingKey,
        String orderCreatedQueue,
        String deadLetterExchange,
        String orderCreatedDlq
) {
}
