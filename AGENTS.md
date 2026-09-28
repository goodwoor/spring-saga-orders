# AGENTS.md — saga-orders

Контекст для работы над этим репозиторием (агенты и разработчики).  
Обновлено: 28 сентября 2026.

Полный план подготовки (вне репо): `C:\Users\GoodWoor\Desktop\java\собесы\актуальный план (сентябрь 2026).md`

> **Для агентов:** начинай с секции [Карта проекта](#карта-проекта--структура-папок) — дерево модулей, порты, схемы БД и ключевые файлы уже зафиксированы; не нужно заново сканировать весь репо.

---

## Карта проекта / структура папок

Maven multi-module (`groupId: saga`, root `artifactId: orders`, Java 21, Spring Boot 4.1.1).

```
saga-orders/
├── pom.xml                          # root: modules common + services; devtools
├── compose.yaml                     # общий Kafka (localhost:9092, KRaft; RF=1 на одном брокере)
├── AGENTS.md                        # этот файл — контекст и карта
├── .cursor/rules/                   # правила Cursor (ask-before-edits и др.)
├── .mvn/wrapper/                    # Maven Wrapper
│
├── common/                          # packaging pom
│   ├── pom.xml                      # modules: dto, gateway; web отложен (после Kafka / перед README)
│   ├── dto/                         # shared JAR (spring-boot plugin skip)
│   │   └── src/main/java/saga/
│   │       ├── events/              # OrderCreated, OrderLine, ReserveCreated, PaymentCompleted, OrderConfirmed
│   │       └── commands/            # CreateReserveCommand, ReserveOrderLine, CreatePaymentCommand, CreateDeliveryCommand (задел)
│   └── gateway/                     # Spring Cloud Gateway (WebMVC)
│       ├── compose.yaml
│       └── src/main/
│           ├── java/saga/ApiGateway.java
│           └── resources/application.properties   # routes → сервисы
│
└── services/                        # packaging pom + общие deps сервисов
    ├── pom.xml                      # JPA, Liquibase, Kafka, Web, Resilience4j,
    │                                # MapStruct 1.6.3 + processor; spring-cloud BOM 2025.1.3
    ├── order/                       # :8083  POST /order → БД + OrderCreated в order-events
    ├── inventory/                   # :8082  GET /inventory → товары
    └── payment/                     # :8084  GET /payment → платежи
```

Типичный слой сервиса (пакеты пока `saga.*`, цель — `saga.{order,inventory,payment}`):

```
*Application, *Controller, *Service, *Mapper, *Repository
entity/          # JPA
dto/             # HTTP records (в payment record лежит в saga, не в dto)
repository/      # только inventory; order/payment — репозиторий в saga
```

Миграции: `01-create-tables.sql` + `02-seed-data.sql`.

### Порты и маршруты

| Компонент | HTTP | Postgres host-port | DB name |
|-----------|------|--------------------|---------|
| Gateway | **8081** | — | — |
| Inventory | **8082** (`/inventory/**`) | **5435** | `inventory` |
| Order | **8083** (`/order/**`) | **5433** | `orders` |
| Payment | **8084** (`/payment/**`) | **5434** | `payments` |
| Kafka | — | broker **9092** | — |

Gateway routes (из `common/gateway/.../application.properties`): `/inventory/**` → 8082, `/order/**` → 8083, `/payment/**` → 8084.

### Схемы БД (Liquibase)

| Сервис | Таблицы | Важное |
|--------|---------|--------|
| **order** | `orders`, `order_items` | FK items→orders; индексы `user_id`, `order_id` |
| **inventory** | `items`, `reservations` | UNIQUE `(order_id, item_id)` |
| **payment** | `payments` | UNIQUE `order_id` (задел под идемпотентность) |

Локальные данные Postgres: `services/*/data/` (в `.gitignore`). Артефакты сборки: `**/target/`.

### Зависимости модулей (кратко)

- **services/**\* наследуют от `services/pom.xml`: WebMVC, JPA, Liquibase, Kafka, Resilience4j, MapStruct, docker-compose, PostgreSQL driver + test starters.
- **common/dto** — лёгкий JAR под **события и команды Kafka** (`saga.events`, `saga.commands`). HTTP response-record’ы живут в сервисе, не здесь.
- **common/gateway** — Gateway WebMVC + Resilience4j; **без** JPA/Kafka/Liquibase.
- **common/web** — отложен (после Kafka / перед README): общий `ProblemDetail` + NotFound/Conflict; кастом сервиса — свой `@ExceptionHandler`. Не класть в dto. Не блокер саги.

### Ключевые файлы «с чего открывать»

| Тема | Путь |
|------|------|
| Root / modules | `pom.xml`, `common/pom.xml`, `services/pom.xml` |
| Kafka infra | `compose.yaml` (KRaft; `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`) |
| Postgres per service | `services/{order,inventory,payment}/compose.yaml` (Kafka сюда не входит — корневой compose) |
| Миграции | `services/*/src/main/resources/db/changelog/migrations/01-create-tables.sql` |
| Сиды | `…/migrations/02-seed-data.sql` |
| Inventory/Payment HTTP | `GET /inventory`, `GET /payment` → Service → Mapper → репозиторий; DTO-record |
| Order HTTP | `POST /order`, `GET /order`, `GET /order/{id}`; `OrderStatus`; create request-DTO |
| Order + N+1 | `findAllWithItems` / `findWithItems` (`@EntityGraph` `orderItems`) |
| Оркестратор | `services/order/.../OrderSaga.java` — listeners `order-events` / `inventory-events` / `payment-events`, шлёт команды |
| Inventory резерв | `InventoryEventListener` (`inventory-commands`) → `InventoryService.reserveItems` → `ReserveCreated` |
| Payment | `PaymentEventListener` (`payment-commands`) → `PaymentService.processPayment` (пока заглушка) |
| События / команды | `common/dto` — `saga.events`, `saga.commands` |
| Gateway routes | `common/gateway/src/main/resources/application.properties` |

---

## Зачем проект

Флагман подготовки к Senior-собесам: закрыть пробел в **распределённых системах** (Saga, Kafka, eventual consistency, Outbox, идемпотентность).

Метрика успеха — **корректность** (компенсация при отказе), не RPS. На собесе: trade-off Saga vs 2PC, оркестрация vs хореография, dual-write → Outbox.

Концептуально близко к ручной реализации того, что делает Axon Framework, но **без Event Sourcing**: состояние в обычных таблицах, события только для координации между сервисами (не как источник истины).

### Что даёт для интервью
- Реальный опыт с eventual consistency и компенсирующими транзакциями
- История про осознанный trade-off: Saga vs 2PC, оркестрация vs хореография
- Понимание «изнутри», что делает Axon — весомее формального знания API
- Материал для behavioral: «расскажи о сложном архитектурном решении»

---

## Идея и стек

Несколько независимых сервисов (заказ / оплата / склад) общаются через Kafka. При отказе шага — компенсирующие транзакции вместо распределённой блокировки.

**Стек:** Spring Boot 4.1.1, Java 21, Kafka, PostgreSQL (БД на сервис), Liquibase, Docker Compose, Maven multi-module. Позже: Testcontainers. Опционально: Resilience4j, CQRS read-модель.

**Модули:**
- `common/dto`, `common/gateway` (`common/web` под HTTP-ошибки — отложен, после Kafka)
- `services/order`, `services/inventory`, `services/payment`

---

## Архитектура — сервисы

| Сервис | Роль | Статусы / таблицы (целевые имена из дизайна) |
|--------|------|-----------------------------------------------|
| **Order** | создание заказа, статус саги | `CREATED` → `AWAITING_PAYMENT` → `CONFIRMED` / `CANCELLED`; после доставки (не сага) → `COMPLETED` |
| **Inventory** | резерв товара | остаток + резервы; резерв откатывается при отмене |
| **Payment** | списание | транзакции по `order_id` (`PENDING` / `SUCCESS` / `FAILED`) |
| **Delivery** (позже) | отгрузки после `CONFIRMED` | свой топик `delivery-events`; Order только читает |

### Database-per-service

У каждого сервиса своя PostgreSQL. Никто не лезет в чужие таблицы. Без общей БД нельзя обернуть заказ + резерв + оплату в одну ACID-транзакцию → нужна Saga на уровне приложения.

**Order:** статус — центральное поле, по нему движется Saga.

**Inventory:** резерв — отдельная операция (уменьшить доступное / зафиксировать резерв), а не мгновенное «списание насовсем», чтобы при отмене заказа можно было откатить резерв.

**Payment:** попытки оплаты как записи по `order_id` — основа идемпотентности при ретраях Kafka.

**Read-модель (CQRS, опционал):** `order_view` — денормализованная таблица под чтение (имена товаров текстом и т.п.), без джойнов между сервисами.

**Dual-write / Outbox:** запись в БД и публикация в Kafka — две операции. Надёжный вариант: событие в `outbox` в той же транзакции, что и данные; отдельный процесс публикует в Kafka. В первой версии допустимо упростить (publish после commit), понимая риск потери события при падении между commit и send.

---

## Kafka — топики и поток

Два вида топиков: `*-commands` (оркестратор → исполнитель) и `*-events` (исполнитель → оркестратор). Топик создаёт владелец через `NewTopic` в своём `*Application`.

| Топик | Пишет | Читает (group) | Есть сейчас | Планируется |
|-------|-------|----------------|-------------|-------------|
| `order-events` | Order | Order (`order-service`) | `OrderCreated`, `OrderConfirmed` (заглушка) | `OrderCancelled` |
| `inventory-commands` | Order | Inventory (`inventory-service`) | `CreateReserveCommand` | команда release резерва |
| `inventory-events` | Inventory | Order (`order-service`) | `ReserveCreated` | `ReserveFailed`, `ReserveReleased` |
| `payment-commands` | Order | Payment (`payment-service`) | `CreatePaymentCommand` | — |
| `payment-events` | Payment | Order (`order-service`) | `PaymentCompleted` (без полей) | `PaymentFailed` |
| `delivery-events` | Delivery (позже) | Order | — | «доставка завершена» → `COMPLETED` |

Классы сообщений — `common/dto`: `saga.events`, `saga.commands`.

**Ключ сообщения = id заказа** — все сообщения одного заказа в одной партиции, строгий порядок.

**Оркестратор** — роль **внутри Order**, не четвёртый сервис: статус саги = `orders.status`. Consumer событий → смотрит статус → публикует следующую **команду** в Kafka (не REST для шагов саги).

**Read-модель** (если есть) — отдельная consumer group на те же топики, обновляет `order_view`.

**Идемпотентность консьюмеров** — Kafka не даёт exactly-once «из коробки» без доп. настройки; особенно Payment не должен списывать дважды при повторном сообщении.

---

## Saga — оркестрация (не хореография)

Оркестрация проще для первой реализации: один координатор вместо логики, размазанной по сервисам.

1. Создать заказ (`CREATED`)
2. Запросить резерв в Inventory
3. Резерв успешен → запросить оплату в Payment
4. Оплата успешна → `CONFIRMED`
5. Оплата fail → компенсация: release резерва → `CANCELLED`
6. Резерв fail → сразу `CANCELLED` (без оплаты)

После саги (не шаг оркестратора): Delivery пишет в `delivery-events`, Order читает → `COMPLETED`.

---

## CQRS (опционал)

- **Write-модель** — Order / Inventory / Payment
- **Read-модель** — `order_view`, обновляется асинхронно из событий
- Учитывать eventual consistency: пока read-модель не догнала — «статус обрабатывается»

## Circuit Breaker (опционал)

На оставшихся синхронных REST (например, первичный запрос) — Resilience4j `@CircuitBreaker` + fallback.

---

## Обязательные паттерны в целевом объёме

- Идемпотентность Payment: не создавать вторую транзакцию по тому же `order_id`
- Outbox (dual-write)
- `@Version` на остатке / понимание `FOR UPDATE`, изоляции, `EXPLAIN ANALYZE`
- Testcontainers: happy-path + компенсация end-to-end

## Осознанно не делаем / позже

- Event Sourcing
- Полноценные таймауты саги (упрощённо — можно)
- Exactly-once продюсера/консьюмера Kafka «в идеале» — не обязателен с v1
- CQRS `order_view`, Resilience4j, Observability (Prometheus/Grafana) — после основного чеклиста

## Нагрузка

Проект не про RPS. Метрика — надёжность: согласованность при отказе любого из трёх сервисов, проверено сценариями отказа.

---

## Состояние кода (ориентир)

**Есть:** multi-module Maven; Gateway 8081; Kafka compose (KRaft, RF=1 для `__consumer_offsets` на одном брокере); Postgres compose на сервис; Liquibase `01` + сиды `02`; сущности (`Order`/`OrderItem`, `Item`/`Reservation`, `Payment`); репозитории (включая `ReservationRepository`); слой Controller → Service → MapStruct → DTO-record; Order — `POST /order`, `GET /order`, `GET /order/{id}`; `OrderStatus`.

Поток саги (ключ = `orderId` строкой везде):
- `OrderWriter` сохраняет заказ (`CREATED`, `@Transactional`), затем `OrderSaga` шлёт `OrderCreated` в `order-events` (после commit, без Outbox).
- `OrderSaga` слушает своё `OrderCreated` → `CreateReserveCommand` в `inventory-commands`.
- Inventory: списывает остаток + `Reservation` в одной транзакции → `ReserveCreated` в `inventory-events`.
- `OrderSaga` на `ReserveCreated` → `CreatePaymentCommand` в `payment-commands`.
- Payment: listener есть, `processPayment` / `PaymentWriter` — заглушки → пустой `PaymentCompleted` в `payment-events`.

**Блокеры happy-path (шаг 4):**
- `PaymentService` не компилируется (висит `private`).
- Payment не сохраняет запись; `PaymentCompleted` без полей.
- Статус заказа не двигается (`AWAITING_PAYMENT` / `CONFIRMED` никто не пишет).
- `sendOrderConfirmedMessage` шлёт `send("order-events", "", "")`.
- Listener'ы Order — один тип на топик; при втором типе события в `order-events` / `inventory-events` сломается. Нужен `@KafkaListener` на классе + `@KafkaHandler` на тип (как в Inventory/Payment).
- Не блокер: Order слушает своё `OrderCreated` ради команды резерва — лишний круг, пересобрать на шаге Outbox.

**Нет / дальше:** события отказа (`ReserveFailed`, `PaymentFailed`) и компенсация (release резерва); идемпотентность Payment; Outbox; `@Version` на `Item`; Testcontainers; README. `common/web` — буфер после фазы A. Delivery — вне текущей фазы (задел `CreateDeliveryCommand` не трогать).

**Локально Kafka:** брокер только в корневом `compose.yaml`. Compose Order поднимает Postgres, не Kafka — без `docker compose -f compose.yaml up -d` клиент крутит reconnect на `localhost:9092`.

**Пакеты:** `saga` + `saga.entity` / `saga.dto` / `saga.repository`. Переименование в `saga.order` и т.п. — осознанно пропущено (косметика, не в scope фазы).

**Локальный reload:** IntelliJ Services по `*Application`. DevTools подхватывает **Ctrl+F9** (Build), не Ctrl+Shift+F9.

---

## План реализации

Календарь, чеклист и график по неделям — **только** во внешнем плане (см. ссылку в начале файла). Здесь — порядок шагов и текущий шаг.

Порядок: миграции → CRUD → Kafka → **happy-path** → компенсация (2 ветки) → идемпотентность Payment → Outbox → `@Version` + `EXPLAIN` → Testcontainers (2 теста) → README.

**Сейчас:** шаг 4, happy-path (заказ → резерв → оплата → `CONFIRMED`). Блокеры — в «Состояние кода».

---

## Принципы работы в этом репо

- Маленькие шаги с видимым прогрессом; не раздувать scope (CQRS/Resilience4j — после основного плана).
- Правило 30 минут: застрял → подсмотреть / спросить → идти дальше.
- README и тесты — часть продукта для собеса, не «потом».
