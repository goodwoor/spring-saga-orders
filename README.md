# saga-orders

Saga (orchestration) на Spring Boot + Kafka + Postgres: Order → Inventory → Payment, компенсация при отказе, Outbox.

## Быстрый старт (разработка в IDEA)

1. Инфра (Kafka, Kafka UI, pgAdmin, demo-ui):

```powershell
docker compose up -d
```

2. В IntelliJ запусти (working directory = **корень репо**):
   - `ApiGateway` (8081)
   - `OrderApplication` (8083)
   - `InventoryApplication` (8082)
   - `PaymentApplication` (8084)

   Postgres поднимется сам через Spring Docker Compose (`profile db`).

3. UI: http://localhost:5173 · Gateway API: http://localhost:8081  
   Kafka UI: http://localhost:8090 · pgAdmin: http://localhost:5050 (`admin@example.com` / `admin`)

Переключение с полного Docker-стека:

```powershell
.\scripts\to-idea.ps1
```

## Полный стек в Docker

Сборка образов + up (долго из‑за `build-image`):

```powershell
.\scripts\docker-up.ps1
```

Если образы уже есть:

```powershell
.\scripts\to-docker.ps1
# или: docker compose --profile db --profile apps up -d
```

Перед этим останови Run Configuration в IDEA (порты 8081–8084).

## Порты

| Что | Порт |
|-----|------|
| Gateway | 8081 |
| Inventory / Order / Payment | 8082 / 8083 / 8084 |
| Kafka | 9092 |
| Kafka UI | 8090 |
| pgAdmin | 5050 |
| demo-ui | 5173 |
| Postgres order / payment / inventory | 5433 / 5434 / 5435 |

Подробный контекст проекта: [AGENTS.md](AGENTS.md).
