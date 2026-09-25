# AGENTS.md — saga-orders

Контекст для работы над этим репозиторием (агенты и разработчики).  
Обновлено: 25 сентября 2026.

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
│   │       ├── OrderCreated.java    # событие Kafka (не HTTP DTO)
│   │       └── OrderLine.java       # позиция в OrderCreated
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
- **common/dto** — лёгкий JAR под **события Kafka** (`OrderCreated`, `OrderLine`). HTTP response-record’ы живут в сервисе, не здесь.
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
| Order → Kafka | `OrderService` + `KafkaTemplate`; топик `NewTopic` в `OrderApplication`; ping-listener `saga.OrderSaga` |
| События | `common/dto` — `OrderCreated`, `OrderLine` |
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

Топики по типу событий (пишет владелец, остальные только читают):
- `order-events` — `OrderCreated`, `OrderCancelled`, `OrderConfirmed`
- `inventory-events` — `InventoryReserved`, `InventoryReservationFailed`, `InventoryReleased`
- `payment-events` — `PaymentCompleted`, `PaymentFailed`
- `delivery-events` (позже) — «доставка завершена»; пишет Delivery, Order подписывается и ставит `COMPLETED`

**Ключ сообщения = id заказа** — все события одного заказа в одной партиции, строгий порядок.

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

**Есть:** multi-module Maven; Gateway 8081; Kafka compose (KRaft, RF=1 для `__consumer_offsets` на одном брокере); Postgres compose на сервис; Liquibase `01` + сиды `02`; сущности (`Order`/`OrderItem`, `Item`/`Reservation`, `Payment`); репозитории; слой Controller → Service → MapStruct → DTO-record; Order — `POST /order` (`CREATED` + позиции) пишет `OrderCreated` в `order-events` (ключ = `orderId` строкой), `GET /order`, `GET /order/{id}`; `OrderStatus`; список товаров/платежей у Inventory/Payment; ping: Order сам слушает `order-events` (`group-id=order-service`) и логирует событие.

**Нет / дальше:** оркестратор (команды резерва/оплаты); `inventory-events` / `payment-events`; консьюмеры Inventory/Payment; `ReservationRepository` (на шаге резерва); Outbox (`send` пока внутри `@Transactional`); `@Version` / `FOR UPDATE`; Testcontainers; README. `common/web` (ошибки HTTP) — отложен, не блокер саги.

**Локально Kafka:** брокер только в корневом `compose.yaml`. Compose Order поднимает Postgres, не Kafka — без `docker compose -f compose.yaml up -d` клиент крутит reconnect на `localhost:9092`.

**Пакеты:** сейчас в основном `saga` (частично `saga.entity` / `saga.dto` / `saga.repository` / `saga.saga`). Цель — `saga.order` / `saga.inventory` / `saga.payment`; ещё не разрослось — переименовать можно до happy-path.

**Локальный reload:** IntelliJ Services по `*Application`. DevTools подхватывает **Ctrl+F9** (Build), не Ctrl+Shift+F9.

---

## План реализации

Срок фазы: ~22 сен — ~2 ноя (~6 недель). Один основной фокус в день — этот проект.

1. ~~Миграции: реальные схемы~~ (сделано; outbox — отдельным шагом).
2. ~~Сущности + репозитории + чтение~~. ~~POST заказа (`CREATED` + позиции) и GET по id~~ — **сделано**, без Kafka. `common/web` отложен.
3. ~~Kafka (продюсер/консьюмер); ключ = order id~~ — **сделано** (пинг `OrderCreated` в `order-events`, проверено логом listener).
4. **Сейчас:** Happy-path: заказ → резерв → оплата → `CONFIRMED`.
5. Компенсация: `PaymentFailed` → release → `CANCELLED`.
6. Идемпотентность Payment.
7. Outbox.
8. БД-глубина: `@Version`, изоляция / `FOR UPDATE`, индекс + `EXPLAIN ANALYZE`.
9. Testcontainers: Postgres + Kafka, happy-path и компенсация.
10. README: mermaid потока + trade-off (Saga vs 2PC, оркестрация vs хореография, dual-write / Outbox).

### Чеклист TODO
- [x] Стартовые миграции (+ сиды). Outbox — позже
- [x] Репозитории + чтение сидов (hello)
- [x] POST заказа + GET по id (без Kafka; `common/web` отложен)
- [x] Конфиги Kafka + проверка сообщений (`OrderCreated` → `order-events`)
- [ ] Сценарий создания заказа end-to-end (резерв → оплата → `CONFIRMED`)
- [ ] Компенсация + идемпотентность Payment
- [ ] Outbox
- [ ] `@Version` / изоляция / EXPLAIN
- [ ] Testcontainers + интеграционные тесты
- [ ] `common/web` (ProblemDetail, NotFound/Conflict; кастом в сервисе)
- [ ] README с архитектурой и trade-off

---

## Принципы работы в этом репо

- Маленькие шаги с видимым прогрессом; не раздувать scope (CQRS/Resilience4j — после чеклиста выше).
- Правило 30 минут: застрял → подсмотреть / спросить → идти дальше.
- README и тесты — часть продукта для собеса, не «потом».

---

## Spring Boot / Maven — справочные ссылки

### Reference Documentation
- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
- [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Docker Compose Support](https://docs.spring.io/spring-boot/4.1.1/reference/features/dev-services.html#features.dev-services.docker-compose)
- [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
- [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j.html)
- [Spring for Apache Kafka](https://docs.spring.io/spring-boot/4.1.1/reference/messaging/kafka.html)
- [Liquibase Migration](https://docs.spring.io/spring-boot/4.1.1/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase)

### Guides
- [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
- [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
- [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
- [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Docker Compose
В проекте есть `compose.yaml` (и compose у сервисов). Образы Postgres — сверять теги с тем, что ожидаете в проде.

### Maven Parent overrides
Из parent POM наследуются в том числе `<license>` и `<developers>`; в project POM стоят пустые overrides. Если смените parent и захотите наследование — overrides нужно убрать.
