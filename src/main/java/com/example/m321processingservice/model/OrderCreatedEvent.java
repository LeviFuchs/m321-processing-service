package com.example.m321processingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Own DTO for the {@code OrderCreated} event, modelled after the shared AsyncAPI contract
 * (see {@code docs/asyncapi.yaml}). This type does NOT depend on Noah's Order Service code.
 * <p>
 * Tolerant Reader: unknown fields (e.g. a future {@code customerReference}) are ignored so that
 * additive changes to the producer's schema do not break this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("product") String product,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("eventVersion") int eventVersion
) {
}
