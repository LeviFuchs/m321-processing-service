package com.example.m321processingservice.service;

import com.example.m321processingservice.config.ProcessingProperties;
import com.example.m321processingservice.messaging.OrderProcessedPublisher;
import com.example.m321processingservice.messaging.ProcessingOutboxRepository;
import com.example.m321processingservice.model.OrderCreatedEvent;
import com.example.m321processingservice.model.OrderProcessedEvent;
import com.example.m321processingservice.observability.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ProcessingInboxRepository inboxRepository;
    private final ProcessingOutboxRepository outboxRepository;
    private final ProcessingProperties processingProperties;

    public OrderProcessingService(ProcessingInboxRepository inboxRepository,
                                   ProcessingOutboxRepository outboxRepository,
                                   ProcessingProperties processingProperties) {
        this.inboxRepository = inboxRepository;
        this.outboxRepository = outboxRepository;
        this.processingProperties = processingProperties;
    }

    @Transactional
    public void process(OrderCreatedEvent event) {
        LoggingContext.put(event.eventId(), event.correlationId(), event.orderId(),
                processingProperties.serviceName());
        try {
            if (!inboxRepository.tryStart(event)) {
                log.info("status=DUPLICATE_IGNORED message=\"Duplicate OrderCreated event ignored, no second OrderProcessed produced\"");
                return;
            }

            log.info("status=RECEIVED message=\"Processing started\"");
            validate(event);
            if (processingProperties.failProduct().equalsIgnoreCase(event.product())) {
                throw new IllegalStateException("Intentional FAIL-DEMO error for retry/DLQ demonstration");
            }
            simulateProcessingTime();

            OrderProcessedEvent processedEvent = toOrderProcessedEvent(event);
            outboxRepository.enqueue(processedEvent);
            inboxRepository.complete(event.eventId());

            log.info("status=COMPLETED message=\"Processing finished; result stored in transactional outbox\"");
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
