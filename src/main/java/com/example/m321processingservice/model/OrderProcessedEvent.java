package com.example.m321processingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Own DTO for the {@code OrderProcessed} event this service publishes, modelled after the
 * shared AsyncAPI contract (see {@code docs/asyncapi.yaml}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderProcessedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("processedAt") Instant processedAt,
        @JsonProperty("status") String status,
        @JsonProperty("eventVersion") String eventVersion
) {

    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String EVENT_VERSION = "1.0";
}
