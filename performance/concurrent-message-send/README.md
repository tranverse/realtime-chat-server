# Concurrent message-send benchmark

This reproducible local benchmark sends real authenticated `POST /api/v1/conversations/{id}/messages` requests concurrently to one conversation. It validates the existing transactional, pessimistic-locking sequence allocator without changing production send behavior.

Run from the backend repository root:

```powershell
.\performance\concurrent-message-send\scripts\run-benchmark.ps1
```

Defaults: MySQL 8.4, 30-second warm-up at 5 VUs, concurrency levels 10/25/50/100, and three repetitions per level. Every repetition uses a new conversation and checks persisted MySQL sequences after k6 completes.

Raw k6 summaries, failure logs, per-run verification, aggregated results, resource samples, and environment details are saved under `results/`. Docker database volumes are removed after the run. These are local pet-project measurements, not production capacity results.
