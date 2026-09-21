# Контекст: saga-orders (пет-проект)

Обновлено: 21 сентября 2026. Ограниченный контекст только по этому репозиторию — для работы над кодом в Cursor.

Полный план подготовки: `C:\Users\GoodWoor\Desktop\java\собесы\актуальный план (сентябрь 2026).md`  
Дизайн (подробно): `C:\Users\GoodWoor\Desktop\java\собесы\практика\Сервис заказов с Saga + CQRS.md`

---

## Зачем проект

Флагман подготовки к Senior-собесам: закрыть пробел в **распределённых системах** (Saga, Kafka, eventual consistency, Outbox, идемпотентность).

Метрика успеха — **корректность** (компенсация при отказе), не RPS. На собесе: trade-off Saga vs 2PC, оркестрация vs хореография, dual-write → Outbox.

---

## Идея и стек

Несколько сервисов (заказ / оплата / склад) общаются через Kafka. При отказе шага — компенсирующие транзакции. Без Event Sourcing: состояние в таблицах, события только для координации.

**Стек:** Spring Boot 4.1.1, Java 21, Kafka, PostgreSQL (БД на сервис), Liquibase, Docker Compose, Maven multi-module. Позже: Testcontainers. Опционально: Resilience4j, CQRS read-модель.

**Модули сейчас:**
- `common/dto`, `common/gateway`
- `services/order`, `services/inventory`, `services/payment`

---

## Архитектура (целевая)

### Сервисы
| Сервис | Роль | Таблицы |
|--------|------|---------|
| Order | создание заказа, статус саги | `orders` (status: CREATED → AWAITING_PAYMENT → CONFIRMED / CANCELLED) |
| Inventory | резерв товара | `stock` (available, reserved); резерв = `available -= N; reserved += N` |
| Payment | списание | `transactions` (order_id, amount, PENDING/SUCCESS/FAILED) |

Database-per-service: нет общей ACID-транзакции → нужна Saga.

### Saga — оркестрация (не хореография)
1. Создать заказ (`CREATED`)
2. Резерв в Inventory
3. Успех → оплата в Payment
4. Успех → `CONFIRMED`
5. Оплата fail → release резерва → `CANCELLED`
6. Резерв fail → сразу `CANCELLED` (без оплаты)

Оркестратор — consumer: слушает события, публикует следующую команду в Kafka (не прямой REST между сервисами для шагов саги).

### Kafka
Топики:
- `order-events` — OrderCreated / Cancelled / Confirmed
- `inventory-events` — Reserved / ReservationFailed / Released
- `payment-events` — Completed / Failed

**Ключ сообщения = id заказа** (порядок событий одного заказа в одной партиции).

### Обязательные паттерны в целевом объёме
- **Идемпотентность Payment:** не создавать вторую `transaction` по тому же order_id.
- **Outbox:** событие в `outbox` в той же транзакции, что и данные; отдельный процесс публикует в Kafka (закрывает dual-write).
- **`@Version` на stock** + понимание `FOR UPDATE` / изоляции / `EXPLAIN ANALYZE`.
- **Testcontainers:** happy-path + компенсация end-to-end.

### Осознанно не делаем / опционал позже
- Event Sourcing
- Полноценные таймауты саги
- CQRS `order_view` (read-модель) — опционал
- Resilience4j Circuit Breaker — опционал
- Observability (Prometheus/Grafana) — опционал

---

## Состояние кода на 21.09

**Есть:** каркас multi-module, Application + hello-контроллеры, gateway-заглушка, Docker/Kafka compose, Liquibase подключён, `ExampleDto` со статическими строками.

**Нет / плейсхолдер:**
- Миграции во всех сервисах создают одинаковую таблицу `users` — заменить на `orders` / `stock` / `transactions` (+ `outbox`).
- Дубль папок `.../migrations/migrations/` — удалить.
- Нет сущностей, репозиториев, Kafka producer/consumer, оркестратора, Outbox, тестов.

Образец слоёв (Controller → Service → Repository, DTO-record, MapStruct): `E:\Dev\java\projects\task1`.

---

## План реализации (порядок шагов)

Срок фазы: ~22 сен — ~2 ноя (~6 недель). Один основной фокус в день — этот проект.

1. Миграции: реальные схемы + удалить дубль `migrations/migrations/`.
2. Сущности + репозитории + простые запросы к БД в каждом сервисе.
3. Kafka-конфиг (продюсер/консьюмер), проверить send/receive; ключ = order id.
4. Happy-path через оркестратор: заказ → резерв → оплата → CONFIRMED.
5. Компенсация: PaymentFailed → release → CANCELLED.
6. Идемпотентность Payment.
7. Outbox.
8. БД-глубина: `@Version` на stock, изоляция / `FOR UPDATE`, индекс + `EXPLAIN ANALYZE`.
9. Testcontainers: Postgres + Kafka, тесты happy-path и компенсации.
10. README: mermaid потока + trade-off (Saga vs 2PC, оркестрация vs хореография, dual-write / Outbox).

### Чеклист TODO
- [ ] Стартовые миграции (orders / stock / transactions / outbox)
- [ ] Удалить `migrations/migrations/`
- [ ] Репозитории + работа с БД
- [ ] Конфиги Kafka + проверка сообщений
- [ ] Сценарий создания заказа end-to-end
- [ ] Компенсация + идемпотентность Payment
- [ ] Outbox
- [ ] `@Version` / изоляция / EXPLAIN
- [ ] Testcontainers + интеграционные тесты
- [ ] README с архитектурой и trade-off

---

## Принципы при работе в этом репо

- Маленькие шаги с видимым прогрессом; не раздувать scope (CQRS/Resilience4j — только после чеклиста выше).
- Правило 30 минут: застрял → подсмотреть / спросить → идти дальше.
- README и тесты — часть продукта для собеса, не «потом».
