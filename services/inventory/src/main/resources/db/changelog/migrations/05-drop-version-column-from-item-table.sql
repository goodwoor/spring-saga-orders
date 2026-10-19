--liquibase formatted sql

--changeset goodwoor:5 logicalFilePath:db/changelog/migrations/05-drop-version-column-from-item-table.sql
ALTER TABLE items
    DROP COLUMN version;
