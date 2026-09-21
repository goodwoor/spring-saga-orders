--liquibase formatted sql

--changeset goodwoor:1
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cost NUMERIC(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    status_changed_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    amount INT NOT NULL,
    cost NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_orders
        FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
