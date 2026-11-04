# Saga latency split by step, from outbox timestamps of the three databases.
# Run after measure-load.ps1 (or any orders). Does not start Docker or the apps.
#
#   relay   = outbox.status_changed_date - outbox.created_at   (poller wait + send + ack)
#   transit = created_at of the next outbox row - PROCESSED of the previous one
#             (Kafka delivery + consumer handler + its transaction)
#
# All timestamps come from Instant.now() in the apps on one host, so they compare directly.
param(
    [int]$LastMinutes = 15,
    [long]$FromOrderId = 0,
    [long]$ToOrderId = [long]::MaxValue
)
$ErrorActionPreference = 'Stop'

$Inv = [Globalization.CultureInfo]::InvariantCulture

function Format-Num([double]$Number, [int]$Digits) {
    return $Number.ToString(('F' + $Digits), $script:Inv)
}

function Get-Percentile([double[]]$Sorted, [double]$Percentile) {
    $n = $Sorted.Length
    if ($n -eq 0) { return [double]::NaN }
    $rank = [Math]::Ceiling(($Percentile / 100.0) * $n)
    if ($rank -lt 1) { $rank = 1 }
    if ($rank -gt $n) { $rank = $n }
    return $Sorted[$rank - 1]
}

function Get-PublishedContainerId([int]$Port) {
    $raw = & docker ps --filter "publish=$Port" --format "{{.ID}}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker ps failed for port ${Port}: $raw"
    }
    $first = @(($raw | Out-String).Trim() -split "`r?`n" | Where-Object { $_.Trim() }) |
        Select-Object -First 1
    if (-not $first) {
        throw "No Postgres container publishing port $Port"
    }
    return $first.Trim()
}

function Invoke-Psql([string]$ContainerId, [string]$Database, [string]$Sql) {
    $raw = & docker exec -e PGPASSWORD=postgres $ContainerId `
        psql -U postgres -d $Database -tA -F '|' -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed for ${Database}: $raw"
    }
    return @(($raw | Out-String).Trim() -split "`r?`n" | Where-Object { $_.Contains('|') })
}

function Read-Outbox([string]$ContainerId, [string]$Database, [string]$Service) {
    $sql = @"
SELECT message_key,
       message_type,
       status,
       EXTRACT(EPOCH FROM created_at) * 1000,
       EXTRACT(EPOCH FROM status_changed_date) * 1000
FROM outbox
WHERE created_at > now() - interval '$LastMinutes minutes'
"@
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in (Invoke-Psql $ContainerId $Database $sql)) {
        $parts = $line.Split('|')
        $orderId = 0L
        if (-not [long]::TryParse($parts[0], [ref]$orderId)) { continue }
        if ($orderId -lt $FromOrderId -or $orderId -gt $ToOrderId) { continue }
        $rows.Add([pscustomobject]@{
            OrderId = $orderId
            Service = $Service
            Type = $parts[1].Substring($parts[1].LastIndexOf('.') + 1)
            Status = $parts[2]
            CreatedMs = [double]::Parse($parts[3], $script:Inv)
            ChangedMs = [double]::Parse($parts[4], $script:Inv)
        })
    }
    return $rows
}

function Read-Orders([string]$ContainerId) {
    $sql = @"
SELECT id,
       status,
       EXTRACT(EPOCH FROM created_at) * 1000,
       EXTRACT(EPOCH FROM status_changed_date) * 1000
FROM orders
WHERE created_at > now() - interval '$LastMinutes minutes'
  AND status IN ('CONFIRMED', 'CANCELLED')
"@
    $orders = @{}
    foreach ($line in (Invoke-Psql $ContainerId 'orders' $sql)) {
        $parts = $line.Split('|')
        $id = [long]$parts[0]
        if ($id -lt $FromOrderId -or $id -gt $ToOrderId) { continue }
        $orders[$id] = [pscustomobject]@{
            Id = $id
            Status = $parts[1]
            CreatedMs = [double]::Parse($parts[2], $script:Inv)
            ChangedMs = [double]::Parse($parts[3], $script:Inv)
        }
    }
    return $orders
}

function Add-Sample($Buckets, [string]$Key, [double]$Value) {
    if (-not $Buckets.Contains($Key)) {
        $Buckets[$Key] = New-Object System.Collections.Generic.List[double]
    }
    $Buckets[$Key].Add($Value)
}

function Format-Bucket([string]$Label, $Values) {
    $sorted = [double[]]@($Values | Sort-Object)
    $n = $sorted.Length
    $sum = 0.0
    foreach ($value in $sorted) { $sum += $value }
    return '{0,-58} n={1,-4} p50={2,8} p95={3,8} mean={4,8} max={5,8}' -f `
        $Label, $n,
        (Format-Num (Get-Percentile $sorted 50) 1),
        (Format-Num (Get-Percentile $sorted 95) 1),
        (Format-Num ($sum / $n) 1),
        (Format-Num $sorted[$n - 1] 1)
}

$ids = @{
    Orders = Get-PublishedContainerId 5433
    Payments = Get-PublishedContainerId 5434
    Inventory = Get-PublishedContainerId 5435
}

$orders = Read-Orders $ids.Orders
$rows = New-Object System.Collections.Generic.List[object]
foreach ($row in (Read-Outbox $ids.Orders 'orders' 'order')) { $rows.Add($row) }
foreach ($row in (Read-Outbox $ids.Inventory 'inventory' 'inventory')) { $rows.Add($row) }
foreach ($row in (Read-Outbox $ids.Payments 'payments' 'payment')) { $rows.Add($row) }

# Per order: chain of outbox rows in creation order. OrderCreated is skipped:
# it is written together with CreateReserveCommand and nobody reads it.
$byOrder = $rows | Where-Object { $_.Type -ne 'OrderCreated' } | Group-Object OrderId

# Keyed by final status, so the success and the payment-failure paths are not mixed.
$buckets = [ordered]@{}
$skipped = 0
foreach ($group in $byOrder) {
    $orderId = [long]$group.Name
    $order = $orders[$orderId]
    if ($null -eq $order) { $skipped++; continue }
    $chain = @($group.Group | Sort-Object CreatedMs)
    if (@($chain | Where-Object { $_.Status -ne 'PROCESSED' }).Count -gt 0) { $skipped++; continue }

    $path = ($chain | ForEach-Object { $_.Type }) -join ' > '
    $prefix = "[$($order.Status)]"
    Add-Sample $buckets "$prefix total (orders.status_changed - created)" ($order.ChangedMs - $order.CreatedMs)
    Add-Sample $buckets "$prefix start  POST tx -> $($chain[0].Type)" ($chain[0].CreatedMs - $order.CreatedMs)

    $relaySum = 0.0
    $transitSum = 0.0
    for ($i = 0; $i -lt $chain.Count - 1; $i++) {
        $current = $chain[$i]
        $next = $chain[$i + 1]
        $relay = $current.ChangedMs - $current.CreatedMs
        $transit = $next.CreatedMs - $current.ChangedMs
        $relaySum += $relay
        $transitSum += $transit
        Add-Sample $buckets ("{0} {1}. relay   {2}:{3}" -f $prefix, ($i + 1), $current.Service, $current.Type) $relay
        Add-Sample $buckets ("{0} {1}. transit -> {2}:{3}" -f $prefix, ($i + 1), $next.Service, $next.Type) $transit
    }
    Add-Sample $buckets "$prefix sum relay" $relaySum
    Add-Sample $buckets "$prefix sum transit" $transitSum
    Add-Sample $buckets "$prefix path: $path" 0
}

Write-Host "Orders in window: $($orders.Count), skipped (unfinished or NEW rows): $skipped"
Write-Host "Milliseconds. relay = outbox row waits for the poller + send + ack; transit = Kafka + next handler."
Write-Host ''
foreach ($key in $buckets.Keys) {
    if ($key -like '*path:*') {
        Write-Host ('{0} (orders: {1})' -f $key, $buckets[$key].Count)
        continue
    }
    Write-Host (Format-Bucket $key $buckets[$key])
}
