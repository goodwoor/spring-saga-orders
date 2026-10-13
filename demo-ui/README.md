# Saga Orders — demo UI

Vite + React screen for interview demos: create orders, watch saga status, inventory and payments.

No Java changes. Browser calls Vite on **5173**; Vite proxies `/order`, `/inventory`, `/payment` to Gateway **8081**.

## Prerequisites

- Node.js 20+ and npm
- Running stack: Kafka (root `compose.yaml`), Postgres per service, Gateway + Order + Inventory + Payment

## Run (recommended — IntelliJ)

1. Once: `cd demo-ui && npm install`
2. **Run → Edit Configurations → + → npm**
   - Name: `demo-ui`
   - Package.json: `demo-ui/package.json`
   - Command: `run`
   - Scripts: `dev`
3. **Compound configuration:** add `demo-ui` together with `ApiGateway`, `OrderApplication`, `InventoryApplication`, `PaymentApplication` (and Docker Compose for Kafka if you use it).
4. Open http://localhost:5173

## Run (CLI)

```bash
cd demo-ui
npm install
npm run dev
```

## Run (Docker Compose, optional)

From repo root. Сервис в [compose.yaml](../compose.yaml) с профилем **`demo-ui`** (Kafka поднимается вместе с UI). Gateway и сервисы — на хосте (IDEA), контейнер ходит на `host.docker.internal:8081`.

```bash
docker compose --profile demo-ui up -d
```

Только UI (Kafka уже запущена):

```bash
docker compose --profile demo-ui up -d demo-ui
```

**IntelliJ Docker Compose:** в Run Configuration → Environment → `COMPOSE_PROFILES=demo-ui`, либо в CLI options: `--profile demo-ui`.

Первый старт ~30 с (`npm ci` в контейнере). `node_modules` — отдельный volume, не затирает локальную установку.

Переопределить Gateway: `VITE_GATEWAY_TARGET` в `compose.yaml`.

## Demo buttons

| Button | Expected result |
|--------|-----------------|
| Happy path | `CONFIRMED` |
| Reserve fail | `CANCELLED` (not enough stock) |
| Payment fail | `CANCELLED` (`userId` 9999) |

Selected order is polled every 1s until terminal status. Lists refresh every ~2.5s.

## Build

```bash
npm run build
npm run preview
```
