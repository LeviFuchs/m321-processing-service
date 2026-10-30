package com.example.m321processingservice.messaging;

import com.example.m321processingservice.config.MessagingProperties;
import com.example.m321processingservice.model.OrderProcessedEvent;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ProcessingOutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final MessagingProperties messagingProperties;

    public ProcessingOutboxRepository(
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper,
            MessagingProperties messagingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.messagingProperties = messagingProperties;
    }

    public void enqueue(OrderProcessedEvent event) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events(event_id, aggregate_id, event_type, routing_key,
                    payload, correlation_id, created_at)
                VALUES (?, ?, 'OrderProcessed', ?, ?, ?, ?)
                """, UUID.fromString(event.eventId()), UUID.fromString(event.orderId()),
                messagingProperties.orderProcessedRoutingKey(), jsonMapper.writeValueAsString(event),
                event.correlationId(), Timestamp.from(event.processedAt()));
    }

    public List<PendingProcessedEvent> claimBatch(String workerId) {
        return jdbcTemplate.query("""
                UPDATE outbox_events SET locked_by = ?, locked_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                WHERE event_id IN (
                    SELECT event_id FROM outbox_events
                    WHERE published_at IS NULL
                      AND (locked_until IS NULL OR locked_until < CURRENT_TIMESTAMP)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 50
                )
                RETURNING event_id, payload
                """, (rs, rowNum) -> new PendingProcessedEvent(
                        rs.getObject("event_id", UUID.class),
                        jsonMapper.readValue(rs.getString("payload"), OrderProcessedEvent.class)), workerId);
    }

    public void markPublished(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET published_at = ?, attempts = attempts + 1, locked_by = NULL, locked_until = NULL
                WHERE event_id = ?
                """, Timestamp.from(Instant.now()), eventId);
    }

    public void markFailed(UUID eventId, String error) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET attempts = attempts + 1, last_error = ?, locked_by = NULL, locked_until = NULL
                WHERE event_id = ?
                """, error, eventId);
    }

    public record PendingProcessedEvent(UUID eventId, OrderProcessedEvent event) {
    }
}
