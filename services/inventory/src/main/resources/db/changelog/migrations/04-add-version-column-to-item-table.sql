--liquibase formatted sql

--changeset goodwoor:4 logicalFilePath:db/changelog/migrations/04-add-version-column-to-item-table.sql
ALTER TABLE items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;