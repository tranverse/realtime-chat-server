# Message-History Pagination Benchmark

## Objective

Compare sequence-based keyset pagination with offset pagination for append-heavy chat history as a conversation grows.

## Environment

The recorded run used Spring Boot 4.0.6, MySQL 8.4, Redis 7.4, Docker, and k6 0.57. Host details are retained in `results/environment.txt`.

## Methodology

The benchmark starts an isolated Docker environment and uses a benchmark-only Spring profile to expose equivalent keyset and offset query endpoints. For each dataset it seeds one conversation, runs a 30-second warm-up, then measures every strategy/position combination for 10 seconds with 10 virtual users. Each case runs three times with page size 50.

The three positions are shallow (start), middle (50% depth), and deep (90% depth). Reported values are aggregated across the three repetitions.

## Dataset and Workload

- Datasets: 10,000; 100,000; and 500,000 messages in one conversation
- Virtual users: 10
- Page size: 50
- Repetitions: 3
- Strategies: keyset (`sequence < beforeSequence`) and offset (`LIMIT 50 OFFSET n`)

## Results

| Messages | Position | Keyset p50/p95/p99 (ms) | Offset p50/p95/p99 (ms) |
| ---: | --- | ---: | ---: |
| 10,000 | shallow | 5.62 / 15.19 / 24.25 | 4.49 / 10.63 / 17.45 |
| 10,000 | middle | 5.26 / 13.76 / 21.66 | 30.91 / 59.16 / 80.92 |
| 10,000 | deep | 4.54 / 10.40 / 16.59 | 52.80 / 106.03 / 142.71 |
| 100,000 | shallow | 5.26 / 12.85 / 21.02 | 5.06 / 12.81 / 20.32 |
| 100,000 | middle | 5.05 / 12.08 / 18.95 | 347.91 / 536.14 / 633.19 |
| 100,000 | deep | 5.07 / 12.91 / 20.98 | 582.45 / 881.79 / 992.73 |
| 500,000 | shallow | 5.34 / 16.20 / 27.36 | 6.74 / 22.59 / 38.06 |
| 500,000 | middle | 5.74 / 17.44 / 29.71 | 1,774.74 / 2,852.44 / 3,099.05 |
| 500,000 | deep | **6.36 / 17.23 / 26.66** | **3,011.10 / 4,380.11 / 4,861.98** |

All recorded requests completed without HTTP errors. The complete aggregate is in `results/summary.csv`; individual k6 summary JSON files provide the retained evidence.

## Query Analysis

Keyset query:

```sql
SELECT ...
FROM messages
WHERE conversation_id = ? AND sequence < ?
ORDER BY sequence DESC
LIMIT 50;
```

Offset query:

```sql
SELECT ...
FROM messages
WHERE conversation_id = ?
ORDER BY sequence DESC
LIMIT 50 OFFSET ?;
```

The `(conversation_id, sequence)` index lets the keyset query begin at the cursor and scan a bounded range. Offset must traverse and discard earlier index entries. The retained MySQL `EXPLAIN FORMAT=JSON` outputs show estimated offset work increasing from roughly 9,100 rows at 10K messages to roughly 449,546 rows at 500K, while the keyset plan continues to use both index columns as a range condition.

Keyset is therefore suited to sequential chat-history navigation. Offset still supports arbitrary positional access, which this API does not require.

## Limitations

- This is a local controlled benchmark, not a production SLA.
- The benchmark uses one conversation and synthetic message content.
- It measures database/API pagination, not browser rendering or WebSocket delivery.
- Host hardware, Docker allocation, buffer-pool state, and MySQL configuration affect absolute latency.
- The benchmark-only endpoints are isolated by the `benchmark` Spring profile and are not enabled in normal application execution.

## Reproduction

Requirements: Docker Desktop with Compose, PowerShell, Java 21, and the benchmark-profile source under `src/main/java/com/tranverse/chatserver/benchmark`.

From the backend repository root:

```powershell
.\performance\message-history\scripts\run-benchmark.ps1
```

Optional parameters control virtual users, warm-up duration, measured duration, and repetitions. The runner recreates the benchmark database, records environment and EXPLAIN output, aggregates results, and removes Docker volumes on completion.
