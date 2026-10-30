package com.example.m321processingservice.messaging;

import com.example.m321processingservice.config.MessagingProperties;
import com.example.m321processingservice.model.OrderProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OrderProcessedPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessedPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public OrderProcessedPublisher(RabbitTemplate rabbitTemplate, MessagingProperties messagingProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    public void publish(OrderProcessedEvent event) {
        publishConfirmed(event);
    }

    public void publishConfirmed(OrderProcessedEvent event) {
        CorrelationData correlationData = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(
                messagingProperties.exchange(),
                messagingProperties.orderProcessedRoutingKey(),
                event,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setCorrelationId(event.correlationId());
                    message.getMessageProperties().setHeader("eventType", "OrderProcessed");
                    message.getMessageProperties().setHeader("eventVersion", event.eventVersion());
                    return message;
                },
                correlationData
        );
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ rejected OrderProcessed: " + confirm.reason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting RabbitMQ confirmation", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("RabbitMQ confirmation failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ confirmation timed out after 10 seconds", exception);
        }
        log.info("status=PUBLISHED message=\"OrderProcessed published\" routingKey={}",
                messagingProperties.orderProcessedRoutingKey());
    }
}
