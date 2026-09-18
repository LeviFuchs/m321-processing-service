package com.example.m321processingservice.messaging;

import com.example.m321processingservice.config.MessagingProperties;
import com.example.m321processingservice.model.OrderProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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
        rabbitTemplate.convertAndSend(
                messagingProperties.exchange(),
                messagingProperties.orderProcessedRoutingKey(),
                event
        );
        log.info("status=PUBLISHED message=\"OrderProcessed published\" routingKey={}",
                messagingProperties.orderProcessedRoutingKey());
    }
}
