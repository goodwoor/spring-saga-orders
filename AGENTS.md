# AGENTS.md — saga-orders

Контекст для работы над этим репозиторием (агенты и разработчики).  
Обновлено: 13 октября 2026.

Полный план подготовки (вне репо): `C:\Users\GoodWoor\Desktop\java\собесы\актуальный план (сентябрь 2026).md`

> **Для агентов:** начинай с секции [Карта проекта](#карта-проекта--структура-папок) — дерево модулей, порты, схемы БД и ключевые файлы уже зафиксированы; не нужно заново сканировать весь репо.

---

## Карта проекта / структура папок

Maven multi-module (`groupId: saga`, root `artifactId: orders`, Java 21, Spring Boot 4.1.1).

```
saga-orders/
├── pom.xml                          # root: modules common + services; devtools
├── compose.yaml                     # include сервисов; Kafka + UI + profiles db/apps
├── README.md                        # краткий запуск (IDEA / full Docker)
├── AGENTS.md                        # этот файл — контекст и карта
├── scripts/                         # docker-up.ps1, to-idea.ps1, to-docker.ps1
├── docker/pgadmin/                  # servers.json + pgpass (3 БД)
├── demo-ui/                         # Vite demo (:5173), в compose без profile
├── .cursor/rules/                   # правила Cursor (ask-before-edits и др.)
├── .mvn/wrapper/                    # Maven Wrapper
│
├── common/                          # packaging pom
│   ├── pom.xml                      # modules: lib + common-services; web отложен
│   ├── lib/                         # packaging pom; boot-plugin skip (библиотеки)
│   │   ├── dto/                     # JAR: saga.events / saga.commands
│   │   └── outbox/                  # JAR: OutBoxProducer + OutBoxScheduler + сущность
│   └── common-services/             # запускаемые общие сервисы (не домен)
│       └── gateway/                 # Spring Cloud Gateway (WebMVC)
│           ├── compose.yaml         # profile apps
│           └── src/main/
│               ├── java/saga/ApiGateway.java
│               └── resources/application.properties   # routes → сервисы
│
└── services/                        # packaging pom + общие deps сервисов
    ├── pom.xml                      # JPA, Liquibase, Kafka, Web, MapStruct 1.6.3;
    │                                # spring-cloud BOM 2025.1.3; deps: dto + outbox
    ├── order/                       # :8083; compose: postgres profile db, app profile apps
    ├── inventory/                   # :8082; то же
    └── payment/                     # :8084; то же
```

Типичный слой сервиса (пакеты пока `saga.*`, цель — `saga.{order,inventory,payment}`):

```
*Application, *Controller, *Service, *Mapper, *Repository
entity/          # JPA
dto/             # HTTP records (в payment record лежит в saga, не в dto)
repository/      # только inventory; order/payment — репозиторий в saga
```

Миграции: `01-create-tables.sql` + `02-seed-data.sql`; у всех `03` (`outbox`); у Order ещё `04–06` (`topic` + `message_type VARCHAR(300)`).

### Порты и маршруты

| Компонент | HTTP | Postgres host-port | DB name |
|-----------|------|--------------------|---------|
| Gateway | **8081** | — | — |
| Inventory | **8082** (`/inventory/**`) | **5435** | `inventory` |
| Order | **8083** (`/order/**`) | **5433** | `orders` |
| Payment | **8084** (`/payment/**`) | **5434** | `payments` |
| Kafka | — | broker **9092** (host); внутри Docker-сети **kafka:29092** | — |
| Kafka UI | **8090** | — | — |
| pgAdmin | **5050** | — | login `admin@example.com` / `admin` |
| demo-ui | **5173** | — | → Gateway `:8081` |

Gateway routes (из `common/common-services/gateway/.../application.properties`): `/inventory/**` → 8082, `/order/**` → 8083, `/payment/**` → 8084 (в Docker — hostname сервиса через env `*_URI`).

### Схемы БД (Liquibase)

| Сервис | Таблицы | Важное |
|--------|---------|--------|
| **order** | `orders`, `order_items`, `outbox` | FK items→orders; индексы `user_id`, `order_id`; outbox: `topic`, UNIQUE `(message_key, message_type)`, индекс `status` |
| **inventory** | `items`, `reservations`, `outbox` | UNIQUE `(order_id, item_id)`; outbox как у Order |
| **payment** | `payments`, `outbox` | UNIQUE `order_id` (идемпотентность); outbox как у Order |

Локальные данные Postgres: `services/*/data/` (в `.gitignore`). Артефакты сборки: `**/target/`.

### Зависимости модулей (кратко)

- **services/**\* наследуют от `services/pom.xml`: WebMVC, JPA, Liquibase, Kafka, MapStruct, docker-compose, PostgreSQL driver + test starters; JAR `dto` + `outbox`.
- **common/lib/dto** — лёгкий JAR под **события и команды Kafka** (`saga.events`, `saga.commands`). HTTP response-record’ы живут в сервисе, не здесь.
- **common/lib/outbox** — библиотека (не сервис): `OutBoxProducer` / `OutBoxScheduler`, таблица в БД **каждого** сервиса. Без compose/порта/`application.properties`.
- **common/common-services/gateway** — Gateway WebMVC; **без** JPA/Kafka/Liquibase.
- **common/web** — отложен (после Kafka / перед README): общий `ProblemDetail` + NotFound/Conflict; кастом сервиса — свой `@ExceptionHandler`. Не класть в dto. Не блокер саги.

### Ключевые файлы «с чего открывать»

| Тема | Путь |
|------|------|
| Root / modules | `pom.xml`, `common/pom.xml`, `services/pom.xml` |
| Запуск | `README.md`, `scripts/to-idea.ps1`, `scripts/to-docker.ps1`, `scripts/docker-up.ps1` |
| Compose | `compose.yaml` (`include` + Kafka dual-listener + kafka-ui + pgadmin + demo-ui) |
| Postgres / apps | `services/{order,inventory,payment}/compose.yaml` — profile **`db`** (Postgres), **`apps`** (образы) |
| Gateway apps | `common/common-services/gateway/compose.yaml` — profile **`apps`** |
| Миграции | `services/*/src/main/resources/db/changelog/migrations/01-create-tables.sql` |
| Сиды | `…/migrations/02-seed-data.sql` |
| Inventory/Payment HTTP | `GET /inventory`, `GET /payment` → Service → Mapper → репозиторий; DTO-record |
| Order HTTP | `POST /order`, `GET /order`, `GET /order/{id}`; `OrderStatus`; create request-DTO |
| Order + N+1 | `findAllWithItems` / `findWithItems` (`@EntityGraph` `orderItems`) |
| Оркестратор | `services/order/.../kafka/OrderSaga.java` — `@KafkaListener` на `inventory-events` + `payment-events`, `@KafkaHandler` на тип; статусы через `OrderWriter`; исходящие — `OutBoxProducer` |
| Outbox | `common/lib/outbox`: `OutBoxProducer.send(Topic, key, message)` (insert в той же TX) + `OutBoxScheduler` (`Class.forName` + колонка `topic` → Kafka → `PROCESSED`) |
| Inventory резерв | `InventoryCommandListener` (`inventory-commands`) → `reserveItems` / `revertReserve` + outbox → `ReserveCreated` / `ReserveFailed` / `ReserveReverted` |
| Payment | `PaymentCommandListener` (`payment-commands`) → `createPayment` + outbox → `PaymentCompleted` / `PaymentFailed` (`userId == 9999` → `FAILED`) |
| События / команды | `common/lib/dto` — `saga.events.order` / `saga.events.inventory` / `saga.events.payment`, `saga.commands` |
| Postman | `postman/collections/Saga-orders/` |
| Gateway routes | `common/common-services/gateway/src/main/resources/application.properties` |

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
- `common/lib/dto`, `common/lib/outbox`, `common/common-services/gateway` (`common/web` под HTTP-ошибки — отложен, после Kafka)
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

**Dual-write / Outbox:** закрыт во всех трёх сервисах. Строка в `outbox` **этого** сервиса в той же TX, что заказ / резерв / платёж; `OutBoxScheduler` в том же JVM публикует в Kafka. Код поллера общий (`common/lib/outbox`), миграция таблицы — в каждом сервисе.

---

## Kafka — топики и поток

Два вида топиков: `*-commands` (оркестратор → исполнитель) и `*-events` (исполнитель → оркестратор). Топик создаёт владелец через `NewTopic` в своём `*Application`.

| Топик | Пишет | Читает (group) | Есть сейчас | Планируется |
|-------|-------|----------------|-------------|-------------|
| `order-events` | Order (`OutBoxScheduler`) | никто (задел: read-модель) | `OrderCreated`, `OrderConfirmed`, `OrderCancelled` | — |
| `inventory-commands` | Order (`OutBoxScheduler`) | Inventory (`inventory-service`) | `CreateReserveCommand`, `RevertReserveCommand` | — |
| `inventory-events` | Inventory (`OutBoxScheduler`) | Order (`order-service`) | `ReserveCreated`, `ReserveFailed`, `ReserveReverted` | — |
| `payment-commands` | Order (`OutBoxScheduler`) | Payment (`payment-service`) | `CreatePaymentCommand` | — |
| `payment-events` | Payment (`OutBoxScheduler`) | Order (`order-service`) | `PaymentCompleted` (`orderId`, `userId`, `paymentId`), `PaymentFailed` (`orderId`, `userId`, `reason`) | — |
| `delivery-events` | Delivery (позже) | Order | — | «доставка завершена» → `COMPLETED` |

Классы сообщений — `common/lib/dto`: `saga.events`, `saga.commands`. Enum топиков — `saga.entity.Topic` в `common/lib/outbox`.

**Ключ сообщения = id заказа** — все сообщения одного заказа в одной партиции, строгий порядок.

**Оркестратор** — роль **внутри Order**, не четвёртый сервис: статус саги = `orders.status`. Consumer событий → смотрит статус → пишет следующую **команду** в `outbox` (не REST, не прямой `KafkaTemplate`). Релей (`OutBoxScheduler` из библиотеки) в каждом сервисе читает `NEW` (`ORDER BY id`) и шлёт в Kafka (`topic` + FQCN).

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

**Есть:** multi-module Maven; Gateway 8081; Kafka compose (KRaft, RF=1 для `__consumer_offsets` на одном брокере); Postgres compose на сервис; Liquibase `01` + сиды `02` + `outbox` в каждом сервисе (Order `03` + `04–06` topic/FQCN; Inventory/Payment `03` сразу с `topic`); сущности (`Order`/`OrderItem`, `Item`/`Reservation`, `Payment`, `OutBoxItem`); репозитории (включая `ReservationRepository`, `OutBoxRepository`); слой Controller → Service → MapStruct → DTO-record; Order — `POST /order`, `GET /order`, `GET /order/{id}`; `OrderStatus`.

Поток саги (ключ = `orderId` строкой везде). Исходящие **из всех трёх сервисов** — только `OutBoxProducer.send(Topic, key, message)` (Jackson 3 `JsonMapper`); `message_type` = FQCN; UNIQUE `(message_key, message_type)` + skip дубля. Библиотека `common/lib/outbox`; `@EnableScheduling` на каждом `*Application`.
- `OrderService.processCreateOrder` (`@Transactional`): `createOrder` (`CREATED`) + `OrderCreated` + `CreateReserveCommand` в outbox (без круга через свой listener).
- Inventory: списывает остаток + `Reservation` + outbox в одной TX → `ReserveCreated`; нехватка / нет товара → `ReserveFailed` в outbox (`NOT_ENOUGH_AMOUNT` / `ITEMS_NOT_FOUND`).
- `OrderSaga.onReserveCreated` (`@Transactional`): `validateAndSetAwaitingPaymentStatus` → при `true` `CreatePaymentCommand` в outbox.
- `OrderSaga.onReserveFailed` (`@Transactional`): `validateAndSetCancelledStatus` → `OrderCancelled` в outbox (оплату не шлёт).
- Payment: `userId == 9999` → `FAILED` + `PaymentFailed` в outbox; иначе `SUCCESS` + `PaymentCompleted`. `Payment.status` — `@Enumerated(STRING)`. Повтор `CreatePaymentCommand`: `findByOrderId` → вернуть существующий, listener шлёт тот же исход. UNIQUE `order_id` — страховка на гонку. Listener: бизнес + outbox в одной TX.
- `OrderSaga.onPaymentCompleted` (`@Transactional`): `validateAndSetConfirmedStatus` → при `true` `OrderConfirmed` в outbox.
- `OrderSaga.onPaymentFailed`: `RevertReserveCommand` в outbox (статус заказа ещё `AWAITING_PAYMENT`).
- Inventory `revertReserve` + outbox в одной TX → `ReserveReverted`.
- `OrderSaga.onReserveReverted` (`@Transactional`): `validateAndSetCancelledStatus` → `OrderCancelled` в outbox.
- `OutBoxScheduler`: `NEW` по `id` ASC → `Class.forName(message_type)` → `KafkaTemplate.send(topic, ...).get()` → `PROCESSED`.

`OrderSaga`: `@KafkaListener(topics = {"inventory-events", "payment-events"})` + `@KafkaHandler` на тип; `isDefault = true` в Order / Inventory / Payment. Trusted packages: `saga.commands,saga.events,saga.events.inventory,saga.events.order,saga.events.payment`.

**Шаг 4 (happy-path) — закрыт:** Postman `POST /order` → `GET /order/{id}` = `CONFIRMED`.

**Шаг 5 компенсация — закрыт (обе ветки):**
- резерв fail → `CANCELLED` (Postman: not enough / item not found);
- оплата fail (`userId: 9999`) → revert резерва → `CANCELLED`.

**Шаг 6 идемпотентность Payment — закрыт кодом.** Ретрай Kafka руками не проверяли; фиксация — на шаге 9 (Testcontainers).

**Шаг 7 Outbox — закрыт во всех сервисах (12.10):** `common/lib/outbox` (колонка `topic`, FQCN, релей без `if/else`). Postman happy-path + обе компенсации.

**Нет / дальше:** `@Version` на `Item`; Testcontainers. `common/web` — буфер после фазы A. Delivery — вне текущей фазы (задел `CreateDeliveryCommand` не трогать). Resilience4j — буфер / фаза C (в pom нет).

**Локальный запуск (два режима, не смешивать порты):**
- **IDEA (основной):** `docker compose up -d` → Kafka + kafka-ui + pgAdmin + demo-ui (без Postgres/apps). Приложения в IDEA; working dir = корень репо; `spring.docker.compose.profiles.active=db` поднимает только Postgres сервиса. Kafka: `localhost:9092`.
- **Full Docker:** `.\scripts\to-docker.ps1` или `docker compose --profile db --profile apps up -d` (нужны образы `saga/*`). Env в compose: datasource + `kafka:29092`, `SPRING_DOCKER_COMPOSE_ENABLED=false`. Переключение: `.\scripts\to-idea.ps1` / `.\scripts\to-docker.ps1`. Первая сборка: `.\scripts\docker-up.ps1`.
- Корневой `compose.yaml` делает `include` сервисов/gateway; Postgres **не** стартует без profile `db` (иначе конфликт с IDEA на 5433/4/5).
- Liquibase SQL: `logicalFilePath:db/changelog/migrations/...` — один и тот же id changeset в IDEA и в fat-jar (иначе Docker ломает миграции на существующих `data/`).

**Пакеты:** `saga` + `saga.entity` / `saga.dto` / `saga.repository`. Переименование в `saga.order` и т.п. — осознанно пропущено (косметика, не в scope фазы).

**Локальный reload:** IntelliJ Services по `*Application`. DevTools подхватывает **Ctrl+F9** (Build), не Ctrl+Shift+F9.

---

## План реализации

Календарь, чеклист и график по неделям — **только** во внешнем плане (см. ссылку в начале файла). Здесь — порядок шагов и текущий шаг.

Порядок: миграции → CRUD → Kafka → **happy-path** → компенсация (2 ветки) → идемпотентность Payment → Outbox → `@Version` + `EXPLAIN` → Testcontainers (2 теста) → README.

**Сейчас:** шаг 8 — `@Version` на `Item` + `EXPLAIN ANALYZE`. Шаг 7 закрыт везде (ретрай Payment — на Testcontainers). README с запуском и Docker-инфра (UI, profiles `db`/`apps`) — есть.

---

## Принципы работы в этом репо

- Маленькие шаги с видимым прогрессом; не раздувать scope (CQRS/Resilience4j — после основного плана).
- Правило 30 минут: застрял → подсмотреть / спросить → идти дальше.
- README и тесты — часть продукта для собеса, не «потом».
