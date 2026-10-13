--liquibase formatted sql

--changeset goodwoor:4 logicalFilePath:db/changelog/migrations/04-add-topic-column-to-outbox-table.sql
ALTER TABLE outbox
    ADD COLUMN topic VARCHAR(30),
    ALTER COLUMN message_type TYPE VARCHAR(300);

--changeset goodwoor:5 logicalFilePath:db/changelog/migrations/04-add-topic-column-to-outbox-table.sql
UPDATE outbox
    SET topic = 'order-events'
    WHERE topic IS NULL;

--changeset goodwoor:6 logicalFilePath:db/changelog/migrations/04-add-topic-column-to-outbox-table.sql
ALTER TABLE outbox
    ALTER COLUMN topic SET NOT NULL;