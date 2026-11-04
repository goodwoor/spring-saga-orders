# Поиск потолка: ступени постоянной частоты заказов (k6, открытая модель).
# Сага считается по базе заказов, а не опросом GET: пропускная способность — конечные статусы в секунду,
# время саги — status_changed_date - created_at. В конце каждой ступени снимаются очередь outbox
# и отставание групп Kafka, во время ступени — docker stats.
#
# Пример: .\scripts\load\run-ceiling.ps1 -Scenario happy -Rates 20,40,60,80,120
param(
    [ValidateSet('happy', 'mixed', 'hot')] [string]$Scenario = 'happy',
    [int[]]$Rates = @(20, 40, 60, 80, 120, 160, 200),
    [int]$StepSec = 60,
    [int]$WarmSec = 15,
    [int]$DrainMaxSec = 120,
    [double]$K6Cpus = 2,
    [string]$Network = 'saga-orders_default',
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $root ("target\load\" + (Get-Date -Format 'yyyyMMdd-HHmmss') + "-$Scenario") }
New-Item -ItemType Directory -Force $OutDir | Out-Null

$dbs = [ordered]@{
    order     = @{ C = 'saga-orders-postgres-order-1';     Db = 'orders' }
    inventory = @{ C = 'saga-orders-postgres-inventory-1'; Db = 'inventory' }
    payment   = @{ C = 'saga-orders-postgres-payment-1';   Db = 'payments' }
}
$final = "('CONFIRMED','CANCELLED')"

function Q([string]$name, [string]$sql) {
    $d = $dbs[$name]
    $out = docker exec $d.C psql -U postgres -d $d.Db -tA -F '|' -c $sql
    if ($LASTEXITCODE -ne 0) { throw "psql $name failed: $sql" }
    return $out
}

function Get-OutboxNew {
    $r = [ordered]@{}
    foreach ($n in $dbs.Keys) { $r[$n] = [int](Q $n "select count(*) from outbox where status = 'NEW'") }
    return $r
}

function Get-KafkaLag {
    $lines = docker exec saga-orders-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups 2>$null
    $lag = @{}
    foreach ($l in $lines) {
        $p = ($l -split '\s+') | Where-Object { $_ -ne '' }
        if ($p.Count -ge 6 -and $p[5] -match '^\d+$') {
            $k = "$($p[0])/$($p[1])"
            if (-not $lag.ContainsKey($k)) { $lag[$k] = 0 }
            $lag[$k] += [int]$p[5]
        }
    }
    return (($lag.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join '; ')
}

# Фоновый сбор docker stats
$statsFile = Join-Path $OutDir 'docker-stats.csv'
$stopFile = Join-Path $OutDir 'stats.stop'
$statsJob = Start-Job -ArgumentList $statsFile, $stopFile -ScriptBlock {
    param($file, $stop)
    while (-not (Test-Path $stop)) {
        $ts = (Get-Date).ToString('o')
        $rows = docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}'
        foreach ($r in $rows) { Add-Content -Path $file -Value "$ts,$r" -Encoding utf8 }
    }
}

$results = @()
try {
    foreach ($rate in $Rates) {
        Write-Host "=== $Scenario, $rate заказов/с, $StepSec с ===" -ForegroundColor Cyan
        $t0 = (Q order "select now()").Trim()
        $h0 = Get-Date
        $summary = "k6-$rate.json"
        # k6 пишет предупреждения в stderr; в PowerShell 5.1 при Stop это стало бы фатальной ошибкой
        $ErrorActionPreference = 'Continue'
        docker run --rm --name saga-k6 --network $Network --cpus $K6Cpus `
            -v "${PSScriptRoot}:/scripts:ro" -v "${OutDir}:/out" `
            -e BASE_URL=http://gateway:8081 -e RATE=$rate -e DURATION="${StepSec}s" -e SCENARIO=$Scenario `
            grafana/k6:0.54.0 run --quiet --summary-export "/out/$summary" /scripts/saga-load.js 2>&1 |
            ForEach-Object { "$_" } | Out-File (Join-Path $OutDir "k6-$rate.log") -Encoding utf8
        $ErrorActionPreference = 'Stop'
        if (-not (Test-Path (Join-Path $OutDir $summary))) { throw "k6 не записал сводку, см. k6-$rate.log" }
        $t1 = (Q order "select now()").Trim()
        $h1 = Get-Date

        # Очередь в момент конца подачи
        $inFlight = [int](Q order "select count(*) from orders where created_at >= '$t0' and status not in $final")
        $obx = Get-OutboxNew
        $lag = Get-KafkaLag

        # Дренаж
        $dw = [Diagnostics.Stopwatch]::StartNew()
        while ($dw.Elapsed.TotalSeconds -lt $DrainMaxSec) {
            $left = [int](Q order "select count(*) from orders where created_at >= '$t0' and status not in $final")
            if ($left -eq 0) { break }
            Start-Sleep -Seconds 2
        }
        $drainSec = [math]::Round($dw.Elapsed.TotalSeconds)
        $stuck = [int](Q order "select count(*) from orders where created_at >= '$t0' and status not in $final")

        $a = "('$t0'::timestamptz + interval '$WarmSec seconds')"
        $b = "'$t1'::timestamptz"
        $lat = (Q order ("select count(*), count(*) filter (where status = 'CONFIRMED'), count(*) filter (where status = 'CANCELLED'), " +
            "round((percentile_cont(0.5) within group (order by extract(epoch from status_changed_date - created_at) * 1000) filter (where status in $final))::numeric), " +
            "round((percentile_cont(0.95) within group (order by extract(epoch from status_changed_date - created_at) * 1000) filter (where status in $final))::numeric), " +
            "round((percentile_cont(0.99) within group (order by extract(epoch from status_changed_date - created_at) * 1000) filter (where status in $final))::numeric), " +
            "round(max(extract(epoch from status_changed_date - created_at) * 1000) filter (where status in $final)::numeric), " +
            "round(extract(epoch from ($b - $a))::numeric, 1) " +
            "from orders where created_at >= $a and created_at < $b")).Trim() -split '\|'
        $win = [double]$lat[7]
        $doneInWin = [int](Q order "select count(*) from orders where created_at >= '$t0' and status in $final and status_changed_date >= $a and status_changed_date < $b")

        $k6 = Get-Content (Join-Path $OutDir $summary) -Raw | ConvertFrom-Json
        $m = $k6.metrics
        $dropped = 0
        if ($m.PSObject.Properties.Name -contains 'dropped_iterations') { $dropped = [int]$m.dropped_iterations.count }

        # Средний CPU контейнеров на установившемся участке
        $cpu = ''
        if (Test-Path $statsFile) {
            $from = $h0.AddSeconds($WarmSec); $to = $h1
            $cpu = (Get-Content $statsFile | ForEach-Object {
                    $p = $_ -split ','
                    if ($p.Count -ge 3) { [pscustomobject]@{ T = [datetime]$p[0]; N = $p[1] -replace '^saga-orders-', '' -replace '-1$', ''; C = [double]($p[2] -replace '%', '') } }
                } | Where-Object { $_.T -ge $from -and $_.T -le $to } | Group-Object N |
                ForEach-Object { [pscustomobject]@{ N = $_.Name; C = ($_.Group | Measure-Object C -Average).Average } } |
                Sort-Object C -Descending | ForEach-Object { "{0}={1:0}%" -f $_.N, $_.C }) -join '; '
        }

        $row = [pscustomobject]@{
            scenario      = $Scenario
            rate          = $rate
            offered_ps    = [math]::Round([int]$lat[0] / $win, 1)
            done_ps       = [math]::Round($doneInWin / $win, 1)
            confirmed     = [int]$lat[1]
            cancelled     = [int]$lat[2]
            saga_p50_ms   = $lat[3]
            saga_p95_ms   = $lat[4]
            saga_p99_ms   = $lat[5]
            saga_max_ms   = $lat[6]
            post_p95_ms   = [math]::Round([double]$m.http_req_duration.'p(95)')
            post_fail_pct = [math]::Round(100 * [double]$m.http_req_failed.value, 2)
            dropped       = $dropped
            in_flight_end = $inFlight
            outbox_new    = ($obx.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '; '
            kafka_lag     = $lag
            drain_s       = $drainSec
            stuck         = $stuck
            cpu           = $cpu
            t0            = $t0
            t1            = $t1
        }
        $results += $row
        $row | Format-List | Out-String | Write-Host
        $results | Export-Csv (Join-Path $OutDir 'steps.csv') -NoTypeInformation -Encoding utf8

        if ($stuck -gt 0) { Write-Host "Не дренировалось за $DrainMaxSec с ($stuck заказов) — дальше не поднимаем." -ForegroundColor Yellow; break }
        Start-Sleep -Seconds 10
    }
}
finally {
    # Если скрипт прервали, контейнер k6 сам не остановится и продолжит подавать заказы
    $ErrorActionPreference = 'Continue'
    docker rm -f saga-k6 2>$null | Out-Null
    New-Item -ItemType File -Force $stopFile | Out-Null
    Wait-Job $statsJob -Timeout 15 | Out-Null
    Remove-Job $statsJob -Force
}

$results | Format-Table scenario, rate, offered_ps, done_ps, saga_p50_ms, saga_p95_ms, saga_p99_ms, post_p95_ms, post_fail_pct, dropped, in_flight_end, drain_s, stuck -AutoSize | Out-String -Width 250 | Write-Host
Write-Host "Результаты: $OutDir"
