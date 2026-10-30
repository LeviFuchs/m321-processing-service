package com.example.m321processingservice.service;

import com.example.m321processingservice.model.OrderCreatedEvent;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessingInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessingInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryStart(OrderCreatedEvent event) {
        return jdbcTemplate.update("""
                INSERT INTO processed_events(event_id, order_id, correlation_id, status)
                VALUES (?, ?, ?, 'PROCESSING')
                ON CONFLICT (event_id) DO NOTHING
                """, UUID.fromString(event.eventId()), UUID.fromString(event.orderId()),
                event.correlationId()) == 1;
    }

    public void complete(String eventId) {
        int updated = jdbcTemplate.update("""
                UPDATE processed_events SET status = 'DONE', completed_at = ? WHERE event_id = ?
                """, Timestamp.from(Instant.now()), UUID.fromString(eventId));
        if (updated != 1) {
            throw new IllegalStateException("Inbox record disappeared for event " + eventId);
        }
    }
}
