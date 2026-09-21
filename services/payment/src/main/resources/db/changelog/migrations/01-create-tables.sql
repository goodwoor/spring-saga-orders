--liquibase formatted sql

--changeset goodwoor:1
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    status_changed_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cost NUMERIC(19, 2) NOT NULL,
    CONSTRAINT uq_payments_order_id
        UNIQUE (order_id)
);

CREATE INDEX idx_payments_user_id ON payments (user_id);
