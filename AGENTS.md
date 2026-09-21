# AGENTS.md — saga-orders

Контекст для работы над этим репозиторием (агенты и разработчики).  
Обновлено: 21 сентября 2026.

Полный план подготовки (вне репо): `C:\Users\GoodWoor\Desktop\java\собесы\актуальный план (сентябрь 2026).md`

> **Для агентов:** начинай с секции [Карта проекта](#карта-проекта--структура-папок) — дерево модулей, порты, схемы БД и ключевые файлы уже зафиксированы; не нужно заново сканировать весь репо.

---

## Карта проекта / структура папок

Maven multi-module (`groupId: saga`, root `artifactId: orders`, Java 21, Spring Boot 4.1.1).

```
saga-orders/
├── pom.xml                          # root: modules common + services
├── compose.yaml                     # общий Kafka (localhost:9092, KRaft)
├── AGENTS.md                        # этот файл — контекст и карта
├── .cursor/rules/                   # правила Cursor (ask-before-edits и др.)
├── .mvn/wrapper/                    # Maven Wrapper
│
├── common/                          # packaging pom
│   ├── pom.xml                      # modules: dto, gateway
│   ├── dto/                         # shared JAR (spring-boot plugin skip)
│   │   └── src/main/java/saga/
│   │       └── ExampleDto.java      # заглушка строк ORDER/INVENTORY/PAYMENT
│   └── gateway/                     # Spring Cloud Gateway (WebMVC)
│       ├── compose.yaml
│       └── src/main/
│           ├── java/saga/ApiGateway.java
│           └── resources/application.properties   # routes → сервисы
│
└── services/                        # packaging pom + общие deps сервисов
    ├── pom.xml                      # JPA, Liquibase, Kafka, Web, Resilience4j,
    │                                # spring-cloud BOM 2025.1.3; depends on dto
    ├── order/
    │   ├── compose.yaml             # postgres-order :5433 / DB orders
    │   └── src/main/
    │       ├── java/saga/
    │       │   ├── OrderApplication.java
    │       │   └── OrderController.java   # GET /order/hello
    │       └── resources/
    │           ├── application.properties # port 8083
    │           └── db/changelog/
    │               ├── db.changelog-master.yaml
    │               └── migrations/01-create-tables.sql
    ├── inventory/
    │   ├── compose.yaml             # postgres-inventory :5435 / DB inventory
    │   └── src/main/…               # InventoryApplication, /inventory/hello, 8082
    └── payment/
        ├── compose.yaml             # postgres-payment :5434 / DB payments
        └── src/main/…               # PaymentApplication, /payment/hello, 8084
```

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

- **services/**\* наследуют от `services/pom.xml`: WebMVC, JPA, Liquibase, Kafka, Resilience4j, docker-compose, PostgreSQL driver + test starters.
- **common/dto** — лёгкий JAR, подключён ко всем сервисам.
- **common/gateway** — Gateway WebMVC + Resilience4j; **без** JPA/Kafka/Liquibase.

### Ключевые файлы «с чего открывать»

| Тема | Путь |
|------|------|
| Root / modules | `pom.xml`, `common/pom.xml`, `services/pom.xml` |
| Kafka infra | `compose.yaml` |
| Postgres per service | `services/{order,inventory,payment}/compose.yaml` |
| Миграции | `services/*/src/main/resources/db/changelog/migrations/01-create-tables.sql` |
| Hello API | `*Controller.java` + `ExampleDto.java` |
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
- `common/dto`, `common/gateway`
- `services/order`, `services/inventory`, `services/payment`

---

## Архитектура — сервисы

| Сервис | Роль | Статусы / таблицы (целевые имена из дизайна) |
|--------|------|-----------------------------------------------|
| **Order** | создание заказа, статус саги | `CREATED` → `AWAITING_PAYMENT` → `CONFIRMED` / `CANCELLED`; `orders` + позиции |
| **Inventory** | резерв товара | остаток + резервы; резерв откатывается при отмене |
| **Payment** | списание | транзакции по `order_id` (`PENDING` / `SUCCESS` / `FAILED`) |

### Database-per-service

У каждого сервиса своя PostgreSQL. Никто не лезет в чужие таблицы. Без общей БД нельзя обернуть заказ + резерв + оплату в одну ACID-транзакцию → нужна Saga на уровне приложения.

**Order:** статус — центральное поле, по нему движется Saga.

**Inventory:** резерв — отдельная операция (уменьшить доступное / зафиксировать резерв), а не мгновенное «списание насовсем», чтобы при отмене заказа можно было откатить резерв.

**Payment:** попытки оплаты как записи по `order_id` — основа идемпотентности при ретраях Kafka.

**Read-модель (CQRS, опционал):** `order_view` — денормализованная таблица под чтение (имена товаров текстом и т.п.), без джойнов между сервисами.

**Dual-write / Outbox:** запись в БД и публикация в Kafka — две операции. Надёжный вариант: событие в `outbox` в той же транзакции, что и данные; отдельный процесс публикует в Kafka. В первой версии допустимо упростить (publish после commit), понимая риск потери события при падении между commit и send.

---

## Kafka — топики и поток

Топики по типу событий:
- `order-events` — `OrderCreated`, `OrderCancelled`, `OrderConfirmed`
- `inventory-events` — `InventoryReserved`, `InventoryReservationFailed`, `InventoryReleased`
- `payment-events` — `PaymentCompleted`, `PaymentFailed`

**Ключ сообщения = id заказа** — все события одного заказа в одной партиции, строгий порядок.

**Оркестратор** — consumer по топикам событий: смотрит статус заказа, публикует следующую **команду** в Kafka (не REST для шагов саги).

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

**Есть:** multi-module Maven; Application + hello-контроллеры (`/order|/inventory|/payment/hello`); Gateway на 8081; корневой Kafka compose; Postgres compose на сервис; Liquibase master + миграции (`orders`/`order_items`, `items`/`reservations`, `payments` + индексы/UNIQUE); `ExampleDto` заглушка.

**Нет / дальше по плану:** JPA-сущности и репозитории, доменные DTO/события, Kafka producer/consumer, оркестратор Saga, Outbox, слой Service, интеграционные тесты / Testcontainers. Пакетов `entity`/`repository`/`kafka` пока нет — всё в `saga.*` на уровне hello.

Образец слоёв (Controller → Service → Repository, DTO-record, MapStruct): `E:\Dev\java\projects\task1`.

---

## План реализации

Срок фазы: ~22 сен — ~2 ноя (~6 недель). Один основной фокус в день — этот проект.

1. Миграции: реальные схемы (актуализировать под текущие `orders` / `items`+`reservations` / `payments` + позже `outbox`).
2. Сущности + репозитории + простые запросы к БД.
3. Kafka (продюсер/консьюмер); ключ = order id.
4. Happy-path: заказ → резерв → оплата → `CONFIRMED`.
5. Компенсация: `PaymentFailed` → release → `CANCELLED`.
6. Идемпотентность Payment.
7. Outbox.
8. БД-глубина: `@Version`, изоляция / `FOR UPDATE`, индекс + `EXPLAIN ANALYZE`.
9. Testcontainers: Postgres + Kafka, happy-path и компенсация.
10. README: mermaid потока + trade-off (Saga vs 2PC, оркестрация vs хореография, dual-write / Outbox).

### Чеклист TODO
- [ ] Стартовые миграции актуальны (+ outbox позже)
- [ ] Репозитории + работа с БД
- [ ] Конфиги Kafka + проверка сообщений
- [ ] Сценарий создания заказа end-to-end
- [ ] Компенсация + идемпотентность Payment
- [ ] Outbox
- [ ] `@Version` / изоляция / EXPLAIN
- [ ] Testcontainers + интеграционные тесты
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
