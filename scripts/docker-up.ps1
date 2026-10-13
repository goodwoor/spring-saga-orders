$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "==> Installing modules (-am install)..."
./mvnw -pl services/order,services/inventory,services/payment,common/common-services/gateway -am `
  clean install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Building images (spring-boot:build-image)..."
./mvnw -pl services/order,services/inventory,services/payment,common/common-services/gateway `
  spring-boot:build-image -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Starting full stack (profiles db + apps)..."
docker compose --profile db --profile apps up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Done. Gateway http://localhost:8081 | Kafka UI :8090 | pgAdmin :5050 | demo-ui :5173"
Write-Host "IDEA mode: docker compose down  →  docker compose up -d  →  Run apps in IDEA (Postgres via profile db)."
