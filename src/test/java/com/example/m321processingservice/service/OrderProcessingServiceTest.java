package com.example.m321processingservice.service;

import com.example.m321processingservice.config.ProcessingProperties;
import com.example.m321processingservice.messaging.ProcessingOutboxRepository;
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
import static org.mockito.Mockito.when;

class OrderProcessingServiceTest {

    // timeMs=0 keeps the tests fast; the delay itself is only relevant for the live demo.
    private final ProcessingProperties processingProperties = new ProcessingProperties(
            0, "processing-service", "FAIL-DEMO");

    private OrderProcessingService service(
            ProcessingInboxRepository inbox,
            ProcessingOutboxRepository outbox) {
        return new OrderProcessingService(inbox, outbox, processingProperties);
    }

    private OrderCreatedEvent sampleOrderCreated() {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "correlation-123",
                "order-456",
                "Widget",
                2,
                Instant.parse("2026-09-18T10:00:00Z"),
                1
        );
    }

    @Test
    void processesOrderCreatedAndPublishesOrderProcessed() {
        ProcessingInboxRepository inbox = mock(ProcessingInboxRepository.class);
        ProcessingOutboxRepository outbox = mock(ProcessingOutboxRepository.class);
        OrderProcessingService service = service(inbox, outbox);
        OrderCreatedEvent orderCreated = sampleOrderCreated();
        when(inbox.tryStart(orderCreated)).thenReturn(true);

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(outbox, times(1)).enqueue(captor.capture());
        OrderProcessedEvent processed = captor.getValue();

        assertThat(processed.orderId()).isEqualTo(orderCreated.orderId());
        assertThat(processed.status()).isEqualTo(OrderProcessedEvent.STATUS_PROCESSED);
        assertThat(processed.eventVersion()).isEqualTo(1);
    }

    @Test
    void correlationIdIsCarriedOverUnchanged() {
        ProcessingInboxRepository inbox = mock(ProcessingInboxRepository.class);
        ProcessingOutboxRepository outbox = mock(ProcessingOutboxRepository.class);
        OrderProcessingService service = service(inbox, outbox);
        OrderCreatedEvent orderCreated = sampleOrderCreated();
        when(inbox.tryStart(orderCreated)).thenReturn(true);

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(outbox).enqueue(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo(orderCreated.correlationId());
    }

    @Test
    void orderProcessedGetsANewEventIdDifferentFromOrderCreated() {
        ProcessingInboxRepository inbox = mock(ProcessingInboxRepository.class);
        ProcessingOutboxRepository outbox = mock(ProcessingOutboxRepository.class);
        OrderProcessingService service = service(inbox, outbox);
        OrderCreatedEvent orderCreated = sampleOrderCreated();
        when(inbox.tryStart(orderCreated)).thenReturn(true);

        service.process(orderCreated);

        ArgumentCaptor<OrderProcessedEvent> captor = ArgumentCaptor.forClass(OrderProcessedEvent.class);
        verify(outbox).enqueue(captor.capture());
        OrderProcessedEvent processed = captor.getValue();

        assertThat(processed.eventId()).isNotEqualTo(orderCreated.eventId());
        assertThat(UUID.fromString(processed.eventId())).isNotNull();
    }

    @Test
    void duplicateOrderCreatedEventIdIsIgnored() {
        ProcessingInboxRepository inbox = mock(ProcessingInboxRepository.class);
        ProcessingOutboxRepository outbox = mock(ProcessingOutboxRepository.class);
        OrderProcessingService service = service(inbox, outbox);
        OrderCreatedEvent orderCreated = sampleOrderCreated();
        when(inbox.tryStart(orderCreated)).thenReturn(true, false);

        service.process(orderCreated);
        service.process(orderCreated); // simulates RabbitMQ redelivery of the same message

        verify(outbox, times(1)).enqueue(org.mockito.ArgumentMatchers.any());
    }
}
