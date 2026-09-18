package com.example.m321processingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the shared RabbitMQ topology this service depends on and configures JSON message
 * conversion.
 * <p>
 * Only the topology this service actually needs is declared here:
 * <ul>
 *     <li>the shared topic exchange (also used by Noah's Order Service)</li>
 *     <li>this service's own competing-consumer queue for {@code OrderCreated}, bound with the
 *     shared routing key</li>
 * </ul>
 * Publishing {@code OrderProcessed} only requires the exchange + routing key; the queue(s) that
 * consume it belong to Noah's Order Service and are declared there, not here.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.exchange(), true, false);
    }

    @Bean
    public Queue orderCreatedQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.orderCreatedQueue()).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue,
                                        TopicExchange orderEventsExchange,
                                        MessagingProperties props) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(orderEventsExchange)
                .with(props.orderCreatedRoutingKey());
    }

    /**
     * Type resolution uses the {@code @RabbitListener} method parameter (INFERRED), not the
     * Spring-specific {@code __TypeId__} header, because the producer (Noah's Order Service) is
     * a separate, potentially non-Spring service and must not be required to set Java/Spring
     * type headers on its messages.
     */
    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
