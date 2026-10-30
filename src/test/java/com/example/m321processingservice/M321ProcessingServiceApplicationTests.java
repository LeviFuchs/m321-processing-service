package com.example.m321processingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.m321processingservice.messaging.ProcessingOutboxRepository;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:processing-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "management.otlp.metrics.export.enabled=false"
})
class M321ProcessingServiceApplicationTests {

    @MockitoBean ProcessingOutboxRepository outboxRepository;

    @Test
    void contextLoads() {
    }

}
