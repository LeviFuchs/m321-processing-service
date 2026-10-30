package com.example.m321processingservice.messaging;

import com.example.m321processingservice.config.ProcessingProperties;
import com.example.m321processingservice.observability.LoggingContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProcessingOutboxPublisher.class);
    private final String workerId = UUID.randomUUID().toString();
    private final ProcessingOutboxRepository repository;
    private final OrderProcessedPublisher publisher;
    private final ProcessingProperties processingProperties;

    public ProcessingOutboxPublisher(
            ProcessingOutboxRepository repository,
            OrderProcessedPublisher publisher,
            ProcessingProperties processingProperties) {
        this.repository = repository;
        this.publisher = publisher;
        this.processingProperties = processingProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:500}")
    public void publishPending() {
        for (ProcessingOutboxRepository.PendingProcessedEvent pending : repository.claimBatch(workerId)) {
            var event = pending.event();
            LoggingContext.put(event.eventId(), event.correlationId(), event.orderId(),
                    processingProperties.serviceName());
            try {
                publisher.publishConfirmed(event);
                repository.markPublished(pending.eventId());
            } catch (RuntimeException exception) {
                repository.markFailed(pending.eventId(), exception.getMessage());
                log.warn("status=OUTBOX_RETRY message=\"OrderProcessed publication failed\" reason={}",
                        exception.getMessage());
            } finally {
                LoggingContext.clear();
            }
        }
    }
}
