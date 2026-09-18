package com.example.m321processingservice.observability;

import org.slf4j.MDC;

/**
 * Centralizes the MDC keys used for structured, correlation-aware logging so that every log
 * line written while processing one event carries eventId, correlationId, orderId and the
 * service name (see logback-spring.xml for the log pattern).
 */
public final class LoggingContext {

    public static final String EVENT_ID = "eventId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String ORDER_ID = "orderId";
    public static final String SERVICE = "service";

    private LoggingContext() {
    }

    public static void put(String eventId, String correlationId, String orderId, String service) {
        MDC.put(EVENT_ID, eventId);
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(ORDER_ID, orderId);
        MDC.put(SERVICE, service);
    }

    public static void clear() {
        MDC.clear();
    }
}
