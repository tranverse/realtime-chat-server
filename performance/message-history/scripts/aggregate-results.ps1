param([string]$ResultsDirectory)

$rows = Get-ChildItem -LiteralPath $ResultsDirectory -Filter 'summary-*.json' | ForEach-Object {
    if ($_.BaseName -notmatch '^summary-(\d+)-(shallow|middle|deep)-(keyset|offset)-r\d+$') { return }
    $parts = $_.BaseName -split '-'
    $data = Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json
    [pscustomobject]@{
        Dataset = [int]$parts[1]
        Position = $parts[2]
        Strategy = $parts[3]
        P50 = [double]$data.metrics.http_req_duration.med
        P95 = [double]$data.metrics.http_req_duration.'p(95)'
        P99 = [double]$data.metrics.http_req_duration.'p(99)'
        Average = [double]$data.metrics.http_req_duration.avg
        RequestsPerSecond = [double]$data.metrics.http_reqs.rate
        ErrorRate = [double]$data.metrics.http_req_failed.value * 100
    }
}

$summary = $rows | Group-Object Dataset, Position, Strategy | ForEach-Object {
    $group = $_.Group
    [pscustomobject]@{
        Dataset = $group[0].Dataset
        Position = $group[0].Position
        Strategy = $group[0].Strategy
        P50Ms = [math]::Round(($group | Measure-Object P50 -Average).Average, 2)
        P95Ms = [math]::Round(($group | Measure-Object P95 -Average).Average, 2)
        P99Ms = [math]::Round(($group | Measure-Object P99 -Average).Average, 2)
        AvgMs = [math]::Round(($group | Measure-Object Average -Average).Average, 2)
        ReqPerSec = [math]::Round(($group | Measure-Object RequestsPerSecond -Average).Average, 2)
        ErrorPercent = [math]::Round(($group | Measure-Object ErrorRate -Average).Average, 4)
    }
} | Sort-Object Dataset, Position, Strategy

$summary | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $ResultsDirectory 'summary.csv')
$summary | Format-Table -AutoSize
