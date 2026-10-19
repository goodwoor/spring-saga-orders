# Saga timings on an already running stand, up to 5 parallel POSTs.
# Prints an ASCII summary. Does not start Docker or the apps.
$ErrorActionPreference = 'Stop'

$Base = 'http://localhost:8081'
$Warmup = 5
$ReadN = 50
$SeqN = 20
$BurstN = 30
$BurstConcurrency = 5
$DeadlineSec = 60
$PollMs = 200
$HappyUserId = 101
$FailUserId = 9999

$Summary = New-Object System.Collections.Generic.List[string]
function Add-Line([string]$Text) {
    $script:Summary.Add($Text)
    Write-Host $Text
}

function Format-Num([double]$Number, [int]$Digits) {
    return $Number.ToString(('F' + $Digits), [Globalization.CultureInfo]::InvariantCulture)
}

function Get-Percentile([double[]]$Sorted, [double]$Percentile) {
    $n = $Sorted.Length
    if ($n -eq 0) { return [double]::NaN }
    $rank = [Math]::Ceiling(($Percentile / 100.0) * $n)
    if ($rank -lt 1) { $rank = 1 }
    if ($rank -gt $n) { $rank = $n }
    return $Sorted[$rank - 1]
}

function Format-StatLine(
    [string]$Label,
    $Values,
    [int]$ErrorCount,
    [double]$WallSec
) {
    $sorted = [double[]]@($Values | Sort-Object)
    $n = $sorted.Length
    if ($n -eq 0) {
        return "$Label n=0 errors=0 errPct=0 p50=na p95=na p99=na min=na max=na rps=0 wallSec=$(Format-Num $WallSec 2)"
    }
    $p50 = Get-Percentile $sorted 50
    $p95 = Get-Percentile $sorted 95
    $p99 = Get-Percentile $sorted 99
    $min = $sorted[0]
    $max = $sorted[$n - 1]
    $sum = 0.0
    foreach ($value in $sorted) { $sum += $value }
    $mean = $sum / $n
    $rps = 0
    if ($WallSec -gt 0) { $rps = $n / $WallSec }
    $errPct = (100.0 * $ErrorCount) / $n
    return @(
        "$Label n=$n errors=$ErrorCount errPct=$(Format-Num $errPct 2)",
        "p50=$(Format-Num $p50 1) p95=$(Format-Num $p95 1) p99=$(Format-Num $p99 1)",
        "mean=$(Format-Num $mean 1) min=$(Format-Num $min 1) max=$(Format-Num $max 1)",
        "rps=$(Format-Num $rps 2) wallSec=$(Format-Num $WallSec 2)"
    ) -join ' '
}

[System.Net.ServicePointManager]::Expect100Continue = $false
[System.Net.ServicePointManager]::DefaultConnectionLimit = 32
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Web.Extensions
$script:JsonSer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$script:JsonSer.MaxJsonLength = 67108864

function ConvertFrom-Body([string]$Text) {
    return $script:JsonSer.DeserializeObject($Text)
}

function Get-JsonItems([string]$Text) {
    $parsed = ConvertFrom-Body $Text
    return ,$parsed
}
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
try { $handler.MaxConnectionsPerServer = 32 } catch { }
$script:Http = New-Object System.Net.Http.HttpClient($handler)
$script:Http.Timeout = [TimeSpan]::FromSeconds(30)

function Invoke-Measured {
    param(
        [ValidateSet('GET', 'POST')][string]$Method,
        [string]$Url,
        [string]$JsonBody
    )
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $resp = $null
    $content = $null
    try {
        if ($Method -eq 'GET') {
            $task = $script:Http.GetAsync($Url)
        } else {
            $content = New-Object System.Net.Http.StringContent(
                $JsonBody,
                [Text.Encoding]::UTF8,
                'application/json'
            )
            $task = $script:Http.PostAsync($Url, $content)
        }
        $resp = $task.GetAwaiter().GetResult()
        $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $sw.Stop()
        $code = [int]$resp.StatusCode
        $ok = ($code -ge 200 -and $code -lt 300)
        return [pscustomobject]@{
            Ok = $ok
            Code = $code
            Ms = [double]$sw.Elapsed.TotalMilliseconds
            Body = $text
            Error = $null
        }
    } catch {
        if ($sw.IsRunning) { $sw.Stop() }
        return [pscustomobject]@{
            Ok = $false
            Code = 0
            Ms = [double]$sw.Elapsed.TotalMilliseconds
            Body = $null
            Error = $_.Exception.Message
        }
    } finally {
        if ($null -ne $resp) { $resp.Dispose() }
        if ($null -ne $content) { $content.Dispose() }
    }
}

function Convert-InvariantDecimal($Value) {
    if ($Value -is [string]) {
        return [decimal]::Parse($Value, [Globalization.CultureInfo]::InvariantCulture)
    }
    return [decimal]$Value
}

function New-OrderJson([long]$UserId, $Item) {
    $costText = ([decimal]$Item.Cost).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
    return '{"userId":' + $UserId +
        ',"orderItems":[{"itemId":' + $Item.Id +
        ',"amount":1,"cost":' + $costText +
        '}],"cost":' + $costText + '}'
}

function Format-ItemIds([object[]]$Items) {
    return ((@($Items) | ForEach-Object { $_.Id }) -join ',')
}

function Get-CycledItem([object[]]$Items, [int]$Index) {
    return $Items[$Index % $Items.Length]
}

function Take-ItemGroup([int]$Count, [int]$Each, [bool]$Consume) {
    $ranked = @()
    foreach ($item in $script:Stock) {
        if ($item.Id -eq $script:BaselineItem.Id) { continue }
        $left = [int]$script:Remaining[$item.Id]
        if ($left -lt $Each) { continue }
        $ranked += [pscustomobject]@{ Item = $item; Left = $left }
    }
    $ranked = @($ranked | Sort-Object @{ Expression = 'Left'; Descending = $true }, @{ Expression = { $_.Item.Id }; Ascending = $true })
    if ($ranked.Count -lt $Count) {
        $dump = ($script:Stock | ForEach-Object { "$($_.Id)=$($script:Remaining[$_.Id])" }) -join ','
        throw "Not enough items. count=$Count each=$Each have=$($ranked.Count) remaining=$dump. Measurement stopped."
    }
    $chosen = @($ranked | Select-Object -First $Count | ForEach-Object { $_.Item })
    if ($Consume) {
        foreach ($item in $chosen) {
            $script:Remaining[$item.Id] = [int]$script:Remaining[$item.Id] - $Each
        }
    }
    return ,$chosen
}

function Get-OrderStatus([long]$OrderId) {
    $result = Invoke-Measured GET "$Base/order/$OrderId"
    if (-not $result.Ok) { return $null }
    $parsed = ConvertFrom-Body $result.Body
    return [string]$parsed['status']
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

function Read-OutboxNew([string]$ContainerId, [string]$Database) {
    $raw = & docker exec -e PGPASSWORD=postgres $ContainerId `
        psql -U postgres -d $Database -tAc "SELECT COUNT(*) FROM outbox WHERE status = 'NEW'" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed for ${Database}: $raw"
    }
    $line = @(($raw | Out-String).Trim() -split "`r?`n" | Where-Object { $_.Trim() -match '^\d+$' }) |
        Select-Object -Last 1
    if (-not $line) {
        throw "No outbox count for ${Database}: $raw"
    }
    return [int]$line.Trim()
}

function Measure-GetPhase([string]$Label, [string]$Url, [int]$Count) {
    $values = New-Object System.Collections.Generic.List[double]
    $errors = 0
    $clock = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $Count; $i++) {
        $result = Invoke-Measured GET $Url
        $values.Add($result.Ms)
        if (-not $result.Ok) { $errors++ }
    }
    $clock.Stop()
    Add-Line (Format-StatLine $Label $values $errors $clock.Elapsed.TotalSeconds)
}

function Invoke-PostWave($JsonBodies, [int]$Concurrency, $Clock) {
    $results = New-Object System.Collections.Generic.List[object]
    $inFlight = New-Object System.Collections.Generic.List[object]
    $next = 0
    while (($next -lt $JsonBodies.Count) -or ($inFlight.Count -gt 0)) {
        while (($next -lt $JsonBodies.Count) -and ($inFlight.Count -lt $Concurrency)) {
            $json = $JsonBodies[$next]
            $next++
            $content = New-Object System.Net.Http.StringContent(
                $json,
                [Text.Encoding]::UTF8,
                'application/json'
            )
            $sw = [Diagnostics.Stopwatch]::StartNew()
            $startMs = $Clock.Elapsed.TotalMilliseconds
            $task = $script:Http.PostAsync("$Base/order", $content)
            $inFlight.Add([pscustomobject]@{
                Task = $task
                Sw = $sw
                Content = $content
                StartMs = $startMs
            })
        }
        $taskArr = New-Object System.Threading.Tasks.Task[] $inFlight.Count
        for ($i = 0; $i -lt $inFlight.Count; $i++) {
            $taskArr[$i] = $inFlight[$i].Task
        }
        $doneIdx = [Threading.Tasks.Task]::WaitAny($taskArr)
        $item = $inFlight[$doneIdx]
        $item.Sw.Stop()
        $inFlight.RemoveAt($doneIdx)
        try {
            $resp = $item.Task.Result
            try {
                $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                $code = [int]$resp.StatusCode
                $ok = ($code -ge 200 -and $code -lt 300)
                $results.Add([pscustomobject]@{
                    Ok = $ok
                    Code = $code
                    Ms = [double]$item.Sw.Elapsed.TotalMilliseconds
                    Body = $text
                    StartMs = [double]$item.StartMs
                    Error = $null
                })
            } finally {
                $resp.Dispose()
            }
        } catch {
            $results.Add([pscustomobject]@{
                Ok = $false
                Code = 0
                Ms = [double]$item.Sw.Elapsed.TotalMilliseconds
                Body = $null
                StartMs = [double]$item.StartMs
                Error = $_.Exception.Message
            })
        } finally {
            $item.Content.Dispose()
        }
    }
    return $results
}

function Start-OutboxWatcher([datetime]$Origin) {
    $sync = [hashtable]::Synchronized(@{
        Origin = $Origin
        Ids = $script:PgIds
        Queue = (New-Object System.Collections.Concurrent.ConcurrentQueue[object])
    })
    $runspace = [runspacefactory]::CreateRunspace()
    $runspace.Open()
    $runspace.SessionStateProxy.SetVariable('Sync', $sync)
    $pipe = [powershell]::Create()
    $pipe.Runspace = $runspace
    [void]$pipe.AddScript({
        $ErrorActionPreference = 'Continue'
        function Read-Count([string]$ContainerId, [string]$Database) {
            $raw = & docker exec -e PGPASSWORD=postgres $ContainerId `
                psql -U postgres -d $Database -tAc "SELECT COUNT(*) FROM outbox WHERE status = 'NEW'" 2>&1
            if ($LASTEXITCODE -ne 0) { throw "psql failed for ${Database}: $raw" }
            $line = @(($raw | Out-String).Trim() -split "`r?`n" | Where-Object { $_.Trim() -match '^\d+$' }) |
                Select-Object -Last 1
            if (-not $line) { throw "No outbox count for ${Database}: $raw" }
            return [int]$line.Trim()
        }
        $deadline = $Sync.Origin.AddSeconds(90)
        while ([DateTime]::UtcNow -lt $deadline) {
            $at = [DateTime]::UtcNow
            $errorText = $null
            $zero = $false
            try {
                $orders = Read-Count $Sync.Ids.Orders 'orders'
                $payments = Read-Count $Sync.Ids.Payments 'payments'
                $inventory = Read-Count $Sync.Ids.Inventory 'inventory'
                $zero = (($orders + $payments + $inventory) -eq 0)
            } catch {
                $orders = -1
                $payments = -1
                $inventory = -1
                $errorText = $_.Exception.Message
            }
            $Sync.Queue.Enqueue([pscustomobject]@{
                OffsetSec = ($at - $Sync.Origin).TotalSeconds
                Orders = $orders
                Payments = $payments
                Inventory = $inventory
                Zero = $zero
                Error = $errorText
            })
            if ($zero) { break }
            Start-Sleep -Seconds 1
        }
    })
    return @{
        Pipe = $pipe
        Handle = $pipe.BeginInvoke()
        Sync = $sync
        Runspace = $runspace
    }
}

function Wait-OutboxWatcher($Watcher) {
    try {
        $Watcher.Pipe.EndInvoke($Watcher.Handle) | Out-Null
    } catch {
        $Watcher.Sync.Queue.Enqueue([pscustomobject]@{
            OffsetSec = -1
            Orders = -1
            Payments = -1
            Inventory = -1
            Zero = $false
            Error = $_.Exception.Message
        })
    } finally {
        $Watcher.Pipe.Dispose()
        $Watcher.Runspace.Close()
        $Watcher.Runspace.Dispose()
    }
    return @($Watcher.Sync.Queue)
}

function Add-OutboxSamples([string]$Label, $Samples) {
    $drain = $null
    foreach ($sample in $Samples) {
        $err = ''
        if ($sample.Error) { $err = " error=$($sample.Error)" }
        Add-Line ("$Label t=$(Format-Num ([double]$sample.OffsetSec) 2) orders=$($sample.Orders) payments=$($sample.Payments) inventory=$($sample.Inventory) zero=$($sample.Zero)$err")
        if ($sample.Zero -and $null -eq $drain) {
            $drain = [double]$sample.OffsetSec
        }
    }
    if ($null -eq $drain) {
        Add-Line "$Label drainSec=na"
    } else {
        Add-Line "$Label drainSec=$(Format-Num $drain 2)"
    }
}

function Measure-SagaSequential(
    [string]$Label,
    [long]$UserId,
    [string]$Expected,
    [int]$Count,
    [object[]]$Items
) {
    Write-Host "==> $Label sequential $Count items=$(Format-ItemIds $Items)"
    $journey = New-Object System.Collections.Generic.List[double]
    $postMs = New-Object System.Collections.Generic.List[double]
    $reached = 0
    $unfinished = 0
    $other = 0
    $httpErrors = 0
    $clock = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $Count; $i++) {
        Write-Host ("    {0} {1}/{2}" -f $Label, ($i + 1), $Count)
        $orderClock = [Diagnostics.Stopwatch]::StartNew()
        $post = Invoke-Measured POST "$Base/order" (New-OrderJson $UserId (Get-CycledItem $Items $i))
        $postMs.Add($post.Ms)
        if (-not $post.Ok) {
            $httpErrors++
            $unfinished++
            Write-Host "    HTTP $($post.Code) $($post.Error)"
            continue
        }
        $orderId = [long](ConvertFrom-Body $post.Body)['id']
        $status = $null
        while ($orderClock.Elapsed.TotalSeconds -lt $DeadlineSec) {
            $status = Get-OrderStatus $orderId
            if ($status -eq 'CONFIRMED' -or $status -eq 'CANCELLED') { break }
            Start-Sleep -Milliseconds $PollMs
        }
        if ($status -eq $Expected) {
            $reached++
            $journey.Add([double]$orderClock.Elapsed.TotalMilliseconds)
        } elseif ($status -eq 'CONFIRMED' -or $status -eq 'CANCELLED') {
            $other++
            Write-Host "    order $orderId status=$status expected=$Expected"
        } else {
            $unfinished++
            Write-Host "    order $orderId unfinished status=$status"
        }
    }
    $clock.Stop()
    $postWall = 0.0
    if ($postMs.Count -gt 0) {
        $postSum = 0.0
        foreach ($value in $postMs) { $postSum += $value }
        $postWall = $postSum / 1000.0
    }
    Add-Line (Format-StatLine "$Label sequential-journey" $journey 0 $clock.Elapsed.TotalSeconds)
    Add-Line (Format-StatLine "$Label sequential-post" $postMs $httpErrors $postWall)
    Add-Line "$Label sequential reached=$reached unfinished=$unfinished otherTerminal=$other httpErrors=$httpErrors wallSec=$(Format-Num $clock.Elapsed.TotalSeconds 2) itemIds=$(Format-ItemIds $Items)"
}

function Measure-SagaBurst(
    [string]$Label,
    [long]$UserId,
    [string]$Expected,
    [int]$Count,
    [int]$Concurrency,
    [object[]]$Items
) {
    Write-Host "==> $Label burst $Count c=$Concurrency items=$(Format-ItemIds $Items)"
    $bodies = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $Count; $i++) {
        $bodies.Add((New-OrderJson $UserId (Get-CycledItem $Items $i)))
    }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $posts = Invoke-PostWave $bodies $Concurrency $clock
    $postWallSec = $clock.Elapsed.TotalSeconds
    $origin = [DateTime]::UtcNow
    $watcher = Start-OutboxWatcher $origin

    $tracked = New-Object System.Collections.Generic.List[object]
    $postMs = New-Object System.Collections.Generic.List[double]
    $httpErrors = 0
    foreach ($post in $posts) {
        $postMs.Add([double]$post.Ms)
        if (-not $post.Ok) {
            $httpErrors++
            Write-Host "    HTTP $($post.Code) $($post.Error)"
            continue
        }
        $orderId = [long](ConvertFrom-Body $post.Body)['id']
        $tracked.Add([pscustomobject]@{
            Id = $orderId
            StartMs = [double]$post.StartMs
            Status = $null
            JourneyMs = $null
        })
    }

    while ($clock.Elapsed.TotalSeconds -lt $DeadlineSec) {
        $pending = @($tracked | Where-Object { $null -eq $_.JourneyMs })
        if ($pending.Count -eq 0) { break }
        foreach ($row in $pending) {
            $status = Get-OrderStatus $row.Id
            if ($status -eq 'CONFIRMED' -or $status -eq 'CANCELLED') {
                $row.Status = $status
                $row.JourneyMs = $clock.Elapsed.TotalMilliseconds - $row.StartMs
            }
        }
        $still = @($tracked | Where-Object { $null -eq $_.JourneyMs })
        if ($still.Count -eq 0) { break }
        Start-Sleep -Milliseconds $PollMs
    }

    foreach ($row in @($tracked | Where-Object { $null -eq $_.JourneyMs })) {
        $row.Status = Get-OrderStatus $row.Id
    }
    $clock.Stop()

    $samples = Wait-OutboxWatcher $watcher
    $journey = New-Object System.Collections.Generic.List[double]
    $reached = 0
    $unfinished = $httpErrors
    $other = 0
    $lastEndSec = 0.0
    foreach ($row in $tracked) {
        if ($row.Status -eq $Expected -and $null -ne $row.JourneyMs) {
            $reached++
            $journey.Add([double]$row.JourneyMs)
            $endSec = ($row.StartMs + $row.JourneyMs) / 1000.0
            if ($endSec -gt $lastEndSec) { $lastEndSec = $endSec }
        } elseif ($row.Status -eq 'CONFIRMED' -or $row.Status -eq 'CANCELLED') {
            $other++
        } else {
            $unfinished++
        }
    }
    $perSec = 0.0
    if ($reached -gt 0) {
        if ($unfinished -gt 0) {
            $perSec = $reached / [double]$DeadlineSec
        } elseif ($lastEndSec -gt 0) {
            $perSec = $reached / $lastEndSec
        }
    }
    Add-Line (Format-StatLine "$Label burst-journey" $journey 0 $clock.Elapsed.TotalSeconds)
    Add-Line (Format-StatLine "$Label burst-post" $postMs $httpErrors $postWallSec)
    Add-Line "$Label burst reached=$reached unfinished=$unfinished otherTerminal=$other httpErrors=$httpErrors perSec=$(Format-Num $perSec 2) lastEndSec=$(Format-Num $lastEndSec 2) itemIds=$(Format-ItemIds $Items)"
    Add-OutboxSamples "$Label outbox" $samples
}

try {
    Write-Host "==> gateway $Base"
    $ping = Invoke-Measured GET "$Base/inventory"
    if (-not $ping.Ok) {
        throw "Gateway :8081 does not answer ($($ping.Code) $($ping.Error)). Measurement stopped."
    }

    $script:PgIds = @{
        Orders = Get-PublishedContainerId 5433
        Payments = Get-PublishedContainerId 5434
        Inventory = Get-PublishedContainerId 5435
    }
    $probeOrders = Read-OutboxNew $script:PgIds.Orders 'orders'
    $probePayments = Read-OutboxNew $script:PgIds.Payments 'payments'
    $probeInventory = Read-OutboxNew $script:PgIds.Inventory 'inventory'

    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    $cs = Get-CimInstance Win32_ComputerSystem
    $ramGb = [Math]::Round($cs.TotalPhysicalMemory / 1GB, 1)
    $java = 'unknown'
    try { $java = (& java -version 2>&1 | Out-String).Trim() } catch { $java = $_.Exception.Message }
    $dockerPs = (& docker ps --format "{{.Names}}|{{.Image}}|{{.Ports}}" 2>&1 | Out-String).Trim()
    $stand = 'idea'
    if ($dockerPs -match 'saga/') { $stand = 'docker-apps' }

    $itemsResult = Invoke-Measured GET "$Base/inventory"
    if (-not $itemsResult.Ok) { throw "GET /inventory failed: $($itemsResult.Code)" }
    $items = Get-JsonItems $itemsResult.Body
    $script:Stock = New-Object System.Collections.Generic.List[object]
    $script:Remaining = @{}
    foreach ($raw in $items) {
        $row = [pscustomobject]@{
            Id = [int]$raw['id']
            Name = [string]$raw['name']
            Cost = Convert-InvariantDecimal $raw['cost']
            Amount = [int]$raw['amount']
        }
        $script:Stock.Add($row)
        $script:Remaining[$row.Id] = $row.Amount
    }
    $need = $Warmup + $SeqN + $BurstN + $BurstN
    $script:BaselineItem = $null
    foreach ($candidateId in @(2, 8)) {
        $candidate = @($script:Stock | Where-Object { $_.Id -eq $candidateId } | Select-Object -First 1)
        if ($candidate.Count -eq 0) { continue }
        if ($candidate[0].Amount -ge $need) {
            $script:BaselineItem = $candidate[0]
            break
        }
    }
    if ($null -eq $script:BaselineItem) {
        $rest = @($script:Stock | Where-Object { $_.Amount -ge $need } | Sort-Object @{ Expression = 'Amount'; Descending = $true }, Id)
        if ($rest.Count -gt 0) { $script:BaselineItem = $rest[0] }
    }
    if ($null -eq $script:BaselineItem) {
        $dump = ($script:Stock | ForEach-Object { "$($_.Id)=$($_.Amount)" }) -join ','
        throw "Not enough stock for baseline need=$need. amounts=$dump. Measurement stopped."
    }
    $multi2Seq = Take-ItemGroup 2 5 $true
    $multi5Seq = Take-ItemGroup 5 3 $true
    $multi5Burst = Take-ItemGroup 5 3 $true
    $multi10Burst = Take-ItemGroup 10 3 $true
    $multi10Pay = Take-ItemGroup 10 3 $false

    $list = Invoke-Measured GET "$Base/order"
    if (-not $list.Ok) { throw "GET /order failed: $($list.Code)" }
    $existing = Get-JsonItems $list.Body
    if ($existing.Count -lt 1) { throw "No existing order for GET /order/{id}." }
    $seedId = [long]$existing[0]['id']

    Add-Line '=== RUN ==='
    Add-Line "startedUtc=$([DateTime]::UtcNow.ToString('o'))"
    Add-Line "stand=$stand"
    Add-Line "os=$([Environment]::OSVersion.VersionString)"
    Add-Line "cpu=$($cpu.Name.Trim()) logical=$($cpu.NumberOfLogicalProcessors)"
    Add-Line "ramGb=$(Format-Num ([double]$ramGb) 1)"
    Add-Line "java=$($java -replace '\s+', ' ')"
    Add-Line "percentile=nearest-rank"
    Add-Line "warmup=$Warmup readN=$ReadN seqN=$SeqN burstN=$BurstN burstC=$BurstConcurrency deadlineSec=$DeadlineSec pollMs=$PollMs"
    Add-Line "itemId=$($script:BaselineItem.Id) itemName=$($script:BaselineItem.Name) itemAmount=$($script:BaselineItem.Amount) itemCost=$(Format-Num ([double]$script:BaselineItem.Cost) 2) stockNeed=$need"
    Add-Line "multi2Seq n=10 each=5 itemIds=$(Format-ItemIds $multi2Seq)"
    Add-Line "multi5Seq n=15 each=3 itemIds=$(Format-ItemIds $multi5Seq)"
    Add-Line "multi5Burst n=15 c=3 each=3 itemIds=$(Format-ItemIds $multi5Burst)"
    Add-Line "multi10Burst n=30 c=5 each=3 itemIds=$(Format-ItemIds $multi10Burst)"
    Add-Line "multi10Pay n=30 c=5 each=3 itemIds=$(Format-ItemIds $multi10Pay)"
    Add-Line "seedOrderId=$seedId ordersBeforeWarmup=$($existing.Count)"
    Add-Line "outboxProbe orders=$probeOrders payments=$probePayments inventory=$probeInventory"
    Add-Line 'dockerPs<<'
    Add-Line $dockerPs
    Add-Line 'dockerPs>>'

    Write-Host '==> warmup GET'
    foreach ($url in @(
            "$Base/inventory",
            "$Base/payment",
            "$Base/order",
            "$Base/order/$seedId"
        )) {
        for ($i = 0; $i -lt $Warmup; $i++) {
            [void](Invoke-Measured GET $url)
        }
    }

    Write-Host '==> warmup POST'
    for ($i = 0; $i -lt $Warmup; $i++) {
        $post = Invoke-Measured POST "$Base/order" (New-OrderJson $HappyUserId $script:BaselineItem)
        if (-not $post.Ok) {
            throw "Warmup POST failed: $($post.Code) $($post.Error)"
        }
        $orderId = [long](ConvertFrom-Body $post.Body)['id']
        $status = $null
        $wait = [Diagnostics.Stopwatch]::StartNew()
        while ($wait.Elapsed.TotalSeconds -lt $DeadlineSec) {
            $status = Get-OrderStatus $orderId
            if ($status -eq 'CONFIRMED' -or $status -eq 'CANCELLED') { break }
            Start-Sleep -Milliseconds $PollMs
        }
        if ($status -ne 'CONFIRMED' -and $status -ne 'CANCELLED') {
            throw "Warmup order $orderId did not finish (status=$status). Measurement stopped."
        }
        Write-Host "    warmup $($i + 1)/$Warmup order $orderId $status"
    }

    $ordersNow = Get-JsonItems (Invoke-Measured GET "$Base/order").Body
    $paymentsNow = Get-JsonItems (Invoke-Measured GET "$Base/payment").Body
    Add-Line "readSize orders=$($ordersNow.Count) payments=$($paymentsNow.Count) items=$($items.Count)"

    Write-Host '==> measured GET'
    Measure-GetPhase 'GET /inventory' "$Base/inventory" $ReadN
    Measure-GetPhase 'GET /payment' "$Base/payment" $ReadN
    Measure-GetPhase 'GET /order' "$Base/order" $ReadN
    Measure-GetPhase 'GET /order/{id}' "$Base/order/$seedId" $ReadN

    Measure-SagaSequential 'happy' $HappyUserId 'CONFIRMED' $SeqN @($script:BaselineItem)
    Measure-SagaBurst 'happy' $HappyUserId 'CONFIRMED' $BurstN $BurstConcurrency @($script:BaselineItem)
    Measure-SagaSequential 'payment-fail' $FailUserId 'CANCELLED' $SeqN @($script:BaselineItem)
    Measure-SagaBurst 'payment-fail' $FailUserId 'CANCELLED' $BurstN $BurstConcurrency @($script:BaselineItem)
    Measure-SagaSequential 'multi2-seq' $HappyUserId 'CONFIRMED' 10 $multi2Seq
    Measure-SagaSequential 'multi5-seq' $HappyUserId 'CONFIRMED' 15 $multi5Seq
    Measure-SagaBurst 'multi5-burst' $HappyUserId 'CONFIRMED' 15 3 $multi5Burst
    Measure-SagaBurst 'multi10-burst' $HappyUserId 'CONFIRMED' 30 5 $multi10Burst
    Measure-SagaBurst 'multi10-pay' $FailUserId 'CANCELLED' 30 5 $multi10Pay

    Add-Line "finishedUtc=$([DateTime]::UtcNow.ToString('o'))"
    Add-Line '=== DONE ==='
} finally {
    if ($script:Http) { $script:Http.Dispose() }
}

$outDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'target'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$outFile = Join-Path $outDir 'load-summary.txt'
$Summary -join "`r`n" | Set-Content -Path $outFile -Encoding UTF8
Write-Host "summary=$outFile"
if (-not ($Summary -contains '=== DONE ===')) { exit 1 }
