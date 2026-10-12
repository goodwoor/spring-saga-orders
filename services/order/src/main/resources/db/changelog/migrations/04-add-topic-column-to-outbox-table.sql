--liquibase formatted sql

--changeset goodwoor:4
ALTER TABLE outbox
    ADD COLUMN topic VARCHAR(30),
    ALTER COLUMN message_type TYPE VARCHAR(300);

--changeset goodwoor:5
UPDATE outbox
    SET topic = 'order-events'
    WHERE topic IS NULL;

--changeset goodwoor:6
ALTER TABLE outbox
    ALTER COLUMN topic SET NOT NULL;