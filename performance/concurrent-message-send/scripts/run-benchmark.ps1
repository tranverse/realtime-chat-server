param(
    [int[]]$ConcurrencyLevels = @(10, 25, 50, 100),
    [int]$Repetitions = 3
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compose = Join-Path $root 'compose.yml'
$results = Join-Path $root 'results/after'
$baseUrl = 'http://localhost:18082/api/v1'

New-Item -ItemType Directory -Force -Path $results | Out-Null
Get-ChildItem -LiteralPath $results -File | Remove-Item -Force

function Invoke-MySql([string]$Sql) {
    $lines = & docker compose -f $compose exec -T mysql env MYSQL_PWD=benchmark-password mysql -uroot -N -B chat_application -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed: $Sql" }
    return ($lines | Select-Object -Last 1)
}

function New-Conversation([string]$Token, [string]$Name) {
    $headers = @{ Authorization = "Bearer $Token" }
    $body = @{ type = 'GROUP'; name = $Name; memberIds = @(); maxMembers = 500; avatar = $null } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$baseUrl/conversations" -Headers $headers -ContentType 'application/json' -Body $body).data.id
}

function Get-Metric($Metrics, [string]$Name, [string]$Field, [double]$Default = 0) {
    $metric = $Metrics.PSObject.Properties[$Name]
    if ($null -eq $metric -or $null -eq $metric.Value.PSObject.Properties[$Field]) { return $Default }
    return [double]$metric.Value.$Field
}

$rows = @()
try {
    & docker compose -f $compose down --volumes --remove-orphans
    & docker compose -f $compose up --build -d --wait --wait-timeout 180 mysql redis backend
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed' }

    $loginBody = @{ email = 'concurrent-send@benchmark.local'; password = 'BenchmarkPassword123!' } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" -ContentType 'application/json' -Body $loginBody
    $token = $login.data.accessToken

    $warmupConversation = New-Conversation $token 'Concurrent send warm-up'
    & docker compose -f $compose run --rm `
        -e BASE_URL=http://backend:8080/api/v1 `
        -e TOKEN=$token `
        -e CONVERSATION_ID=$warmupConversation `
        k6 run /scripts/warmup.k6.js
    if ($LASTEXITCODE -ne 0) { throw 'Warm-up failed' }

    $environment = @(
        "Date: $(Get-Date -Format o)",
        "OS: $([System.Environment]::OSVersion.VersionString)",
        "CPU: $((Get-CimInstance Win32_Processor | Select-Object -First 1).Name)",
        "Logical processors: $([System.Environment]::ProcessorCount)",
        "RAM bytes: $((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory)",
        "Java: $((& java -version 2>&1 | Select-Object -First 1))",
        'Spring Boot: 4.0.6',
        'MySQL: 8.4',
        'Redis: 7.4',
        "Docker: $((& docker version --format '{{.Server.Version}}'))",
        "Concurrency levels: $($ConcurrencyLevels -join ', ') sends",
        'Warm-up: 30s at 5 VUs',
        "Repetitions: $Repetitions"
    )
    Set-Content -LiteralPath (Join-Path $results 'environment.txt') -Value $environment

    foreach ($concurrency in $ConcurrencyLevels) {
        for ($repetition = 1; $repetition -le $Repetitions; $repetition++) {
            $conversationId = New-Conversation $token "Concurrent $concurrency run $repetition"
            $runId = "c$concurrency-r$repetition"
            $summaryName = "summary-$runId.json"
            $consoleName = "console-$runId.log"
            $lockBeforeResult = Invoke-MySql "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time';"
            $lockBefore = [long](($lockBeforeResult -split "`t")[-1])

            & docker compose -f $compose run --rm `
                -e BASE_URL=http://backend:8080/api/v1 `
                -e TOKEN=$token `
                -e CONVERSATION_ID=$conversationId `
                -e CONCURRENCY=$concurrency `
                -e RUN_ID=$runId `
                k6 run --console-output "/results/after/$consoleName" --summary-export "/results/after/$summaryName" /scripts/concurrent-send.k6.js
            if ($LASTEXITCODE -ne 0) { throw "k6 failed for $runId" }

            $lockAfterResult = Invoke-MySql "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time';"
            $lockAfter = [long](($lockAfterResult -split "`t")[-1])
            $verification = (Invoke-MySql "SELECT COUNT(*), COUNT(DISTINCT sequence), COALESCE(MIN(sequence),0), COALESCE(MAX(sequence),0), CASE WHEN COUNT(*)=0 THEN 0 ELSE MAX(sequence)-MIN(sequence)+1-COUNT(DISTINCT sequence) END FROM messages WHERE conversation_id=UNHEX(REPLACE('$conversationId','-',''));" ) -split "`t"
            $persisted = [int]$verification[0]
            $distinct = [int]$verification[1]
            $minimum = [long]$verification[2]
            $maximum = [long]$verification[3]
            $missing = [long]$verification[4]
            $duplicates = $persisted - $distinct

            $summary = Get-Content -Raw -LiteralPath (Join-Path $results $summaryName) | ConvertFrom-Json
            $successful = [int](Get-Metric $summary.metrics 'successful_requests' 'count')
            $failed = $concurrency - $successful
            $backendId = (& docker compose -f $compose ps -q backend).Trim()
            $resource = if ($backendId) { & docker stats --no-stream --format '{{json .}}' $backendId | ConvertFrom-Json } else { $null }

            $rows += [pscustomobject]@{
                Concurrency = $concurrency
                Repetition = $repetition
                TotalRequests = $concurrency
                Successful = $successful
                Failed = $failed
                Persisted = $persisted
                DuplicateSequences = $duplicates
                MissingSequences = $missing
                MinSequence = $minimum
                MaxSequence = $maximum
                OrderingValid = ($successful -eq $persisted -and $duplicates -eq 0 -and $missing -eq 0 -and $minimum -eq 1 -and $maximum -eq $persisted)
                P50Ms = [math]::Round((Get-Metric $summary.metrics 'http_req_duration' 'med'), 2)
                P95Ms = [math]::Round((Get-Metric $summary.metrics 'http_req_duration' 'p(95)'), 2)
                P99Ms = [math]::Round((Get-Metric $summary.metrics 'http_req_duration' 'p(99)'), 2)
                AvgMs = [math]::Round((Get-Metric $summary.metrics 'http_req_duration' 'avg'), 2)
                ReqPerSec = [math]::Round((Get-Metric $summary.metrics 'http_reqs' 'rate'), 2)
                ErrorPercent = [math]::Round(($failed / $concurrency * 100), 2)
                LockWaitMs = $lockAfter - $lockBefore
                BackendCpu = if ($resource) { $resource.CPUPerc } else { '' }
                BackendMemory = if ($resource) { $resource.MemUsage } else { '' }
            }
        }
    }

    $rows | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $results 'runs.csv')
    $aggregate = $rows | Group-Object Concurrency | ForEach-Object {
        $group = $_.Group
        [pscustomobject]@{
            ConcurrentSends = [int]$_.Name
            Success = [math]::Round(($group | Measure-Object Successful -Average).Average, 2)
            Failed = [math]::Round(($group | Measure-Object Failed -Average).Average, 2)
            Persisted = [math]::Round(($group | Measure-Object Persisted -Average).Average, 2)
            DuplicateSequences = ($group | Measure-Object DuplicateSequences -Maximum).Maximum
            MissingSequences = ($group | Measure-Object MissingSequences -Maximum).Maximum
            MinSequence = ($group | Measure-Object MinSequence -Minimum).Minimum
            MaxSequence = ($group | Measure-Object MaxSequence -Maximum).Maximum
            OrderingValid = -not ($group.OrderingValid -contains $false)
            P50Ms = [math]::Round(($group | Measure-Object P50Ms -Average).Average, 2)
            P95Ms = [math]::Round(($group | Measure-Object P95Ms -Average).Average, 2)
            P99Ms = [math]::Round(($group | Measure-Object P99Ms -Average).Average, 2)
            AvgMs = [math]::Round(($group | Measure-Object AvgMs -Average).Average, 2)
            ReqPerSec = [math]::Round(($group | Measure-Object ReqPerSec -Average).Average, 2)
            ErrorPercent = [math]::Round(($group | Measure-Object ErrorPercent -Average).Average, 2)
            LockWaitMs = [math]::Round(($group | Measure-Object LockWaitMs -Average).Average, 2)
        }
    } | Sort-Object ConcurrentSends
    $aggregate | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $results 'summary.csv')
    $aggregate | Format-Table -AutoSize
}
finally {
    & docker compose -f $compose logs --no-color backend |
        Select-String 'Duplicate entry.*uk_message_conversation_sequence' |
        Select-Object -First 20 |
        Set-Content -LiteralPath (Join-Path $results 'backend-errors.log')
    & docker compose -f $compose down --volumes --remove-orphans
}
