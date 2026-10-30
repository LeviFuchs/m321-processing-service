CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    correlation_id VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    locked_by VARCHAR(100),
    locked_until TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_processing_outbox_pending
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
