Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "==> Stop full-stack apps + Postgres (free 5433/4/5 and 8081-8084)..."
docker compose --profile db --profile apps stop `
  order inventory payment gateway `
  postgres-order postgres-inventory postgres-payment

Write-Host "==> Ensure shared infra (Kafka, UIs) is up..."
docker compose up -d

Write-Host "Ready for IDEA: Run Gateway + Order + Inventory + Payment (working dir = repo root)."
Write-Host "Postgres will start via spring.docker.compose.profiles.active=db."
