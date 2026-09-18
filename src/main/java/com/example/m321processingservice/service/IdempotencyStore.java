package com.example.m321processingservice.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@code OrderCreated} eventIds have already been processed, so redelivered
 * messages (RabbitMQ gives at-least-once delivery) are not processed a second time.
 * <p>
 * <b>School-project scope only.</b> This is a thread-safe in-memory store, which is enough for a
 * single JVM. It is deliberately NOT sufficient once this service is scaled horizontally
 * (see README "Skalierung / Idempotenz"): each instance has its own memory, so two instances can
 * each independently accept the same eventId and both process it. A production deployment needs
 * a shared, persistent store (e.g. a database table with a unique constraint on eventId, or
 * Redis) so all instances see the same dedup state, and so restarts don't lose it.
 */
@Component
public class IdempotencyStore {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * Records the eventId as processed and reports whether it was already known.
     *
     * @return {@code true} if this eventId was already processed before (duplicate),
     *         {@code false} if this is the first time it is seen.
     */
    public boolean isDuplicate(String eventId) {
        return !processedEventIds.add(eventId);
    }
}
