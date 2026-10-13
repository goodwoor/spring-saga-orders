Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "==> Stop IDEA-managed Postgres (project names order/inventory/payment) if any..."
docker rm -f order-postgres-order-1 inventory-postgres-inventory-1 payment-postgres-payment-1 2>$null | Out-Null

Write-Host "==> Start full stack (profiles db + apps). Stop IDEA Run configs first (ports 8081-8084)!"
docker compose --profile db --profile apps up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Gateway http://localhost:8081"
