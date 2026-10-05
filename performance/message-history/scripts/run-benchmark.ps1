param(
    [int]$VirtualUsers = 10,
    [string]$WarmupDuration = "30s",
    [string]$MeasuredDuration = "10s",
    [int]$Repetitions = 3
)

$ErrorActionPreference = "Stop"
$benchmarkRoot = Split-Path -Parent $PSScriptRoot
$results = Join-Path $benchmarkRoot "results"
$compose = Join-Path $benchmarkRoot "compose.yml"
$project = "message-history-benchmark"
$baseUrl = "http://backend:8080/api/v1"

New-Item -ItemType Directory -Force -Path $results | Out-Null
Get-ChildItem -LiteralPath $results -File | Remove-Item -Force

function Wait-ForBackend {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $health = Invoke-WebRequest -Uri "http://127.0.0.1:18081/actuator/health" -UseBasicParsing -TimeoutSec 3
            if ($health.StatusCode -eq 200) { return }
        } catch { Start-Sleep -Seconds 2 }
    }
    throw "Benchmark backend did not become healthy"
}

function Run-K6([string[]]$Arguments) {
    & docker compose -p $project -f $compose --profile benchmark run --rm @Arguments
    if ($LASTEXITCODE -ne 0) { throw "k6 failed with exit code $LASTEXITCODE" }
}

try {
    docker compose -p $project -f $compose down --volumes --remove-orphans
    docker compose -p $project -f $compose up --build --detach mysql redis backend
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose startup failed" }
    Wait-ForBackend

    $loginBody = @{ email = "history-benchmark@example.com"; password = "BenchmarkPassword123!" } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18081/api/v1/auth/login" -ContentType "application/json" -Body $loginBody
    $token = $login.data.accessToken
    if (-not $token) { throw "Benchmark login did not return an access token" }
    $metadata = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18081/api/v1/benchmark/message-history/metadata" -Headers @{ Authorization = "Bearer $token" }
    $conversationId = $metadata.data.conversationId
    if (-not $conversationId) { throw "Benchmark metadata did not return a conversation ID" }

    @(
        "Date: $(Get-Date -Format o)",
        "OS: $([System.Environment]::OSVersion.VersionString)",
        "CPU: $((Get-CimInstance Win32_Processor | Select-Object -First 1).Name)",
        "Logical processors: $([System.Environment]::ProcessorCount)",
        "RAM bytes: $((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory)",
        "Java: $(java -version 2>&1 | Select-Object -First 1)",
        "Spring Boot: 4.0.6",
        "MySQL: 8.4",
        "Redis: 7.4",
        "Docker: $(docker version --format '{{.Server.Version}}')",
        "Page size: 50",
        "VUs: $VirtualUsers",
        "Warm-up per dataset: $WarmupDuration",
        "Measured duration per repetition: $MeasuredDuration",
        "Repetitions: $Repetitions"
    ) | Set-Content -LiteralPath (Join-Path $results "environment.txt")

    foreach ($dataset in @(10000, 100000, 500000)) {
        Write-Host "Seeding $dataset messages..."
        Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18081/api/v1/benchmark/message-history/$conversationId/seed?messages=$dataset" -Headers @{ Authorization = "Bearer $token" } | Out-Null

        Run-K6 @('-e', "BASE_URL=$baseUrl", '-e', "TOKEN=$token", '-e', "CONVERSATION_ID=$conversationId", '-e', "DATASET=$dataset", '-e', "VUS=$VirtualUsers", '-e', "WARMUP_DURATION=$WarmupDuration", 'k6', 'run', '/scripts/warmup.k6.js')

        $deepOffset = [math]::Floor($dataset * 0.9)
        $deepCursor = $dataset - $deepOffset + 1
        $explainFile = Join-Path $results "explain-$dataset.txt"
        docker compose -p $project -f $compose exec -T mysql mysql -uroot -pbenchmark-password chat_application -e "EXPLAIN FORMAT=JSON SELECT sequence, content FROM messages WHERE conversation_id=UNHEX(REPLACE('$conversationId','-','')) AND sequence < $deepCursor ORDER BY sequence DESC LIMIT 50; EXPLAIN FORMAT=JSON SELECT sequence, content FROM messages WHERE conversation_id=UNHEX(REPLACE('$conversationId','-','')) ORDER BY sequence DESC LIMIT 50 OFFSET $deepOffset;" | Set-Content -LiteralPath $explainFile

        foreach ($position in @('shallow', 'middle', 'deep')) {
            foreach ($strategy in @('offset', 'keyset')) {
                for ($repeat = 1; $repeat -le $Repetitions; $repeat++) {
                    $summary = "/results/summary-$dataset-$position-$strategy-r$repeat.json"
                    Run-K6 @('-e', "BASE_URL=$baseUrl", '-e', "TOKEN=$token", '-e', "CONVERSATION_ID=$conversationId", '-e', "DATASET=$dataset", '-e', "POSITION=$position", '-e', "STRATEGY=$strategy", '-e', "VUS=$VirtualUsers", '-e', "DURATION=$MeasuredDuration", 'k6', 'run', '--summary-export', $summary, '/scripts/pagination.k6.js')
                }
            }
        }
    }

    & (Join-Path $PSScriptRoot "aggregate-results.ps1") -ResultsDirectory $results
} finally {
    docker compose -p $project -f $compose down --volumes --remove-orphans
}
