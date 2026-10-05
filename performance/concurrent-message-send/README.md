# Concurrent Message-Send Benchmark

## Objective

Measure same-conversation write contention and verify that concurrent requests preserve the conversation-local message-sequence invariant.

## Environment

The recorded run used Spring Boot 4.0.6, MySQL 8.4, Redis 7.4, Docker, and k6 0.57. Exact host details are retained in `results/*/environment.txt`.

## Methodology

The harness starts a clean Docker environment, authenticates a benchmark user, warms the application for 30 seconds at 5 virtual users, and creates a separate conversation for every repetition. k6 then issues real authenticated `POST /api/v1/conversations/{id}/messages` requests simultaneously. Each level runs three times.

After every run, the script queries MySQL and verifies persisted count, distinct sequence count, minimum/maximum sequence, and gaps. Aggregated values below are means of the three repetitions.

## Workload

- Concurrent sends to one conversation: 10, 25, 50, and 100
- Repetitions: 3 per level
- A new conversation for each repetition
- All senders use the same authenticated benchmark account

## Results

Final fixed results:

| Concurrent sends | Successful | Failed | Duplicates | Missing | p50 (ms) | p95 (ms) | p99 (ms) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 10 | 0 | 0 | 0 | 106.79 | 210.68 | 218.50 |
| 25 | 25 | 0 | 0 | 0 | 233.76 | 438.12 | 455.44 |
| 50 | 50 | 0 | 0 | 0 | 566.72 | 969.52 | 1,004.89 |
| 100 | 100 | 0 | 0 | 0 | 911.77 | 1,299.90 | 1,331.85 |

At every tested level, persisted sequences were contiguous from 1 through the requested send count. Detailed per-run k6 summaries and aggregated CSV files are retained under `results/after/`. The failing baseline summaries are retained under `results/before/` for technical comparison.

## Concurrency Analysis

The benchmark exposed a MySQL `REPEATABLE READ` ordering problem:

```text
Before: membership SELECT -> consistent snapshot -> conversation lock
        -> stale latest-sequence read -> duplicate allocation

After:  conversation lock -> membership validation -> latest-sequence read
        -> insert -> commit
```

Before the change, the average failure rate was approximately 88-90% across the tested levels because transactions could read a stale sequence after waiting for the lock. The final implementation acquires `PESSIMISTIC_WRITE` on the conversation before any database-backed validation. `UNIQUE(conversation_id, sequence)` remains the final database safeguard.

The lock deliberately serializes writes to one conversation. The increasing latency and InnoDB row-lock time at higher concurrency are the expected consistency/throughput trade-off; different conversations lock different rows.

## Limitations

- This is a local controlled benchmark, not a production SLA or a global capacity limit.
- One authenticated account and one conversation maximize contention but do not model a complete user distribution.
- Host scheduling, Docker resources, and database configuration affect latency.
- It does not test multi-instance WebSocket delivery.

## Reproduction

Requirements: Docker Desktop with Compose, PowerShell, Java 21, and available ports used by `compose.yml`.

From the backend repository root:

```powershell
.\performance\concurrent-message-send\scripts\run-benchmark.ps1
```

The runner removes its benchmark volumes in `finally`, writes new summaries to `results/after/`, and fails if k6 or integrity verification fails.
