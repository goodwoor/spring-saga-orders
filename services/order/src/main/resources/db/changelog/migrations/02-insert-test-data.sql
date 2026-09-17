--liquibase formatted sql

--changeset goodwoor:6
--comment Наполнение таблицы пользователей (users)
INSERT INTO users (id, first_name, second_name, status) VALUES
(1, 'Иван', 'Иванов', 'ACTIVE'),
(2, 'Мария', 'Петрова', 'ACTIVE'),
(3, 'Алексей', 'Сидоров', 'BANNED')
ON CONFLICT DO NOTHING;

