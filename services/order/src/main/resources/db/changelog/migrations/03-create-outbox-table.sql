--liquibase formatted sql

--changeset goodwoor:3
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    message_key VARCHAR(50) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(15) NOT NULL,
    status_changed_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_outbox_unique_message
            UNIQUE (message_key, message_type)
);

CREATE INDEX idx_outbox_status ON outbox (status);