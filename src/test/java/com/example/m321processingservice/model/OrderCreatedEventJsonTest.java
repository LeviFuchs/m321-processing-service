package com.example.m321processingservice.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Tolerant Reader requirement: an OrderCreated payload with an additional, unknown
 * field (e.g. a future "customerReference") must still deserialize successfully.
 * <p>
 * Deliberately does NOT rely on the app-wide FAIL_ON_UNKNOWN_PROPERTIES=false setting
 * (JacksonConfig): this mapper is a plain default one, so the test actually exercises
 * OrderCreatedEvent's own {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 */
class OrderCreatedEventJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void unknownAdditionalFieldsDoNotBreakDeserialization() {
        String json = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "correlationId": "corr-1",
                  "orderId": "order-1",
                  "product": "Widget",
                  "quantity": 3,
                  "createdAt": "2026-09-18T10:00:00Z",
                  "eventVersion": "1.0",
                  "customerReference": "ABC"
                }
                """;

        OrderCreatedEvent event = mapper.readValue(json, OrderCreatedEvent.class);

        assertThat(event.eventId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.product()).isEqualTo("Widget");
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));
    }
}
