package com.example.m321processingservice.service;

import com.example.m321processingservice.config.ProcessingProperties;
import com.example.m321processingservice.messaging.OrderProcessedPublisher;
import com.example.m321processingservice.model.OrderCreatedEvent;
import com.example.m321processingservice.model.OrderProcessedEvent;
import com.example.m321processingservice.observability.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Core workflow: validate -> simulate processing time -> build and publish OrderProcessed.
 * Idempotency is checked first, so a redelivered OrderCreated event never runs the workflow
 * (and never produces a second OrderProcessed event) twice.
 */
@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    private final IdempotencyStore idempotencyStore;
    private final OrderProcessedPublisher publisher;
    private final ProcessingProperties processingProperties;

    public OrderProcessingService(IdempotencyStore idempotencyStore,
                                   OrderProcessedPublisher publisher,
                                   ProcessingProperties processingProperties) {
        this.idempotencyStore = idempotencyStore;
        this.publisher = publisher;
        this.processingProperties = processingProperties;
    }

    public void process(OrderCreatedEvent event) {
        LoggingContext.put(event.eventId(), event.correlationId(), event.orderId(),
                processingProperties.serviceName());
        try {
            // Marked as seen before validation/publish on purpose: this prevents two concurrent
            // redeliveries of the same eventId from both processing it. Trade-off: if processing
            // fails after this point, RabbitMQ's redelivery of the same eventId will now be
            // treated as a duplicate and skipped rather than retried. A production-grade store
            // would track a per-eventId status (e.g. PENDING/DONE) to allow retrying failures.
            if (idempotencyStore.isDuplicate(event.eventId())) {
                log.info("status=DUPLICATE_IGNORED message=\"Duplicate OrderCreated event ignored, no second OrderProcessed produced\"");
                return;
            }

            log.info("status=RECEIVED message=\"Processing started\"");
            validate(event);
            simulateProcessingTime();

            OrderProcessedEvent processedEvent = toOrderProcessedEvent(event);
            publisher.publish(processedEvent);

            log.info("status=COMPLETED message=\"Processing finished\"");
        } finally {
            LoggingContext.clear();
        }
    }

    private void validate(OrderCreatedEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("OrderCreated event is missing orderId");
        }
        if (event.product() == null || event.product().isBlank()) {
            throw new IllegalArgumentException("OrderCreated event is missing product");
        }
        if (event.quantity() == null || event.quantity() <= 0) {
            throw new IllegalArgumentException("OrderCreated event has invalid quantity: " + event.quantity());
        }
    }

    private void simulateProcessingTime() {
        try {
            Thread.sleep(processingProperties.timeMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processing was interrupted", e);
        }
    }

    private OrderProcessedEvent toOrderProcessedEvent(OrderCreatedEvent event) {
        return new OrderProcessedEvent(
                UUID.randomUUID().toString(),
                event.correlationId(),
                event.orderId(),
                Instant.now(),
                OrderProcessedEvent.STATUS_PROCESSED,
                OrderProcessedEvent.EVENT_VERSION
        );
    }
}
