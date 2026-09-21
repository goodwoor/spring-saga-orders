--liquibase formatted sql

--changeset goodwoor:1 runOnChange:true
CREATE TABLE items (
    id BIGSERIAL PRIMARY KEY,
    cost NUMERIC(19, 2) NOT NULL,
    amount INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT
);

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    reserved_amount INT NOT NULL,
    CONSTRAINT fk_reservations_item
        FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT uq_reservations_order_item
        UNIQUE (order_id, item_id)
);

CREATE INDEX idx_reservations_item_id ON reservations (item_id);
