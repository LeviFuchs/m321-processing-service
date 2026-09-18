package com.example.m321processingservice.messaging;

import com.example.m321processingservice.model.OrderCreatedEvent;
import com.example.m321processingservice.service.OrderProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Competing consumer on the shared {@code OrderCreated} queue. Multiple instances of this
 * service can run this listener at the same time; RabbitMQ dispatches each message to exactly
 * one instance (round-robin among connected consumers), which is what makes horizontal scaling
 * of this service work without any coordination between instances.
 */
@Component
public class OrderCreatedListener {

    private final OrderProcessingService orderProcessingService;

    public OrderCreatedListener(OrderProcessingService orderProcessingService) {
        this.orderProcessingService = orderProcessingService;
    }

    @RabbitListener(queues = "${app.messaging.order-created-queue}")
    public void onOrderCreated(OrderCreatedEvent event) {
        orderProcessingService.process(event);
    }
}
