package com.example.m321processingservice.service;

import com.example.m321processingservice.config.ProcessingProperties;
import com.example.m321processingservice.messaging.OrderProcessedPublisher;
import com.example.m321processingservice.model.OrderCreatedEvent;
import com.example.m321processingservice.model.OrderProcessedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderProcessingServiceTest {

    // timeMs=0 keeps the tests fast; the delay itself is only relevant for the live demo.
    private final ProcessingProperties processingProperties = new ProcessingProperties(0, "processing-service");

    private OrderCreatedEvent sampleOrderCreated() {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "correlation-123",
                "order-456",
                "Widget",
                2,
                Instant.parse("2026-09-18T10:00:00Z"),
                "1.0"
        );
    }

    @Test
    void processesOrderCreatedAndPublishesOrderProcessed() {
        OrderProcessedPublisher publisher = mock(OrderProcessedPublisher.class);
        OrderProcessingService service = new OrderProcessingService(new IdempotencyStore(), publisher, processingProperties);
        OrderCreatedEvent orderCreated = sampleOrderCreated();

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(publisher, times(1)).publish(captor.capture());
        OrderProcessedEvent processed = captor.getValue();

        assertThat(processed.orderId()).isEqualTo(orderCreated.orderId());
        assertThat(processed.status()).isEqualTo(OrderProcessedEvent.STATUS_PROCESSED);
    }

    @Test
    void correlationIdIsCarriedOverUnchanged() {
        OrderProcessedPublisher publisher = mock(OrderProcessedPublisher.class);
        OrderProcessingService service = new OrderProcessingService(new IdempotencyStore(), publisher, processingProperties);
        OrderCreatedEvent orderCreated = sampleOrderCreated();

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo(orderCreated.correlationId());
    }

    @Test
    void orderProcessedGetsANewEventIdDifferentFromOrderCreated() {
        OrderProcessedPublisher publisher = mock(OrderProcessedPublisher.class);
        OrderProcessingService service = new OrderProcessingService(new IdempotencyStore(), publisher, processingProperties);
        OrderCreatedEvent orderCreated = sampleOrderCreated();

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(publisher).publish(captor.capture());
        OrderProcessedEvent processed = captor.getValue();

        assertThat(processed.eventId()).isNotEqualTo(orderCreated.eventId());
        assertThat(UUID.fromString(processed.eventId())).isNotNull();
    }

    @Test
    void duplicateOrderCreatedEventIdIsIgnored() {
        OrderProcessedPublisher publisher = mock(OrderProcessedPublisher.class);
        OrderProcessingService service = new OrderProcessingService(new IdempotencyStore(), publisher, processingProperties);
        OrderCreatedEvent orderCreated = sampleOrderCreated();

        service.process(orderCreated);
        service.process(orderCreated); // simulates RabbitMQ redelivery of the same message

        verify(publisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }
}
