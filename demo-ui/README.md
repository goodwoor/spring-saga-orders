# Saga Orders — demo UI

Vite + React: создание заказов, статус саги, inventory и payments.

Браузер → Vite **5173**; прокси `/order`, `/inventory`, `/payment` → Gateway **8081**.

## Prerequisites

- Node.js 20+ (для локального `npm run dev`)
- Инфра: `docker compose up -d` из корня репо (Kafka + demo-ui в Docker **или** только Kafka)
- Приложения: Gateway + Order + Inventory + Payment (IDEA) **или** full Docker (`--profile db --profile apps`)

## Run (IntelliJ)

1. Once: `cd demo-ui && npm install`
2. **Run → Edit Configurations → + → npm**
   - Name: `demo-ui`
   - Package.json: `demo-ui/package.json`
   - Command: `run` / Scripts: `dev`
3. Compound: `demo-ui` + `ApiGateway` + `OrderApplication` + `InventoryApplication` + `PaymentApplication`
4. http://localhost:5173

## Run (CLI)

```bash
cd demo-ui
npm install
npm run dev
```

## Run (Docker Compose)

`demo-ui` в корневом [compose.yaml](../compose.yaml) **без profile** — поднимается вместе с `docker compose up -d`.

Контейнер ходит на Gateway через `host.docker.internal:8081` (сервисы на хосте или published ports).

Первый старт ~30 с (`npm ci`). `node_modules` — volume.

Переопределить Gateway: `VITE_GATEWAY_TARGET` в `compose.yaml`.

## Demo buttons

| Button | Expected result |
|--------|-----------------|
| Happy path | `CONFIRMED` |
| Reserve fail | `CANCELLED` (not enough stock) |
| Payment fail | `CANCELLED` (`userId` 9999) |

Selected order polled every 1s until terminal status. Lists ~2.5s.

## Build

```bash
npm run build
npm run preview
```
