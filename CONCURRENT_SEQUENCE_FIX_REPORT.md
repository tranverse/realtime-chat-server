# Concurrent Message Sequence Fix Report

## Root cause

The benchmark failure was reproducible on MySQL 8.4. `MessageService.send` previously executed these database operations inside one Spring transaction:

1. `requireActiveMember` ran a normal membership `SELECT`.
2. `findByIdForUpdate` ran a pessimistic locking query for the conversation (`SELECT ... FOR UPDATE`).
3. `findTopByConversationIdOrderBySequenceDesc` ran a normal `SELECT ... ORDER BY sequence DESC LIMIT 1`.
4. The service inserted the message and updated `conversations.last_message_id`.

MySQL used its default `REPEATABLE READ` isolation. The initial normal membership query could establish a consistent-read snapshot before the transaction obtained the conversation lock. Waiting transactions acquired the lock one at a time, but their later non-locking sequence lookup could still read the earlier snapshot. Multiple transactions therefore calculated the same next sequence. MySQL then raised error 1062 on `uk_message_conversation_sequence`; the API mapped it to HTTP 400 `INVALID_CONVERSATION`.

The evidence distinguishes this from validation, pool exhaustion, and lock timeout failures: backend logs contained duplicate `(conversation_id, sequence)` inserts, and every committed subset remained unique only because the database constraint rejected competing writes.

## Transaction timeline before fix

```text
Transaction A                         Transaction B
membership SELECT → snapshot S1      membership SELECT → snapshot S1
conversation SELECT FOR UPDATE       waits for conversation lock
MAX(sequence) from S1 → 1
INSERT sequence 1; COMMIT
                                      acquires conversation lock
                                      MAX(sequence) from S1 → still 1
                                      INSERT sequence 1 → duplicate key
                                      ROLLBACK
```

The pessimistic lock existed, but it was acquired too late relative to the first consistent read.

## Selected fix

`MessageService.send` now locks the conversation before any database-backed authorization or user lookup:

```text
validate in-memory request
→ SELECT conversation FOR UPDATE
→ validate active membership
→ load active sender
→ read latest message sequence
→ insert message
→ update conversation
→ commit
```

The public REST/STOMP API, DTOs, response codes, sequence format, unique constraint, and pessimistic-locking design are unchanged. The production change is a one-line reordering.

## Why this fix is correct

An InnoDB locking read is a current read and does not create the earlier consistent snapshot that caused the stale sequence lookup. The transaction first waits for and obtains the conversation lock. Only then does the membership query establish its normal read view, after the preceding sender has committed. Consequently the latest-sequence query observes that committed message and allocates the next value.

The conversation row remains locked until commit, so same-conversation allocators cannot overlap. The unique `(conversation_id, sequence)` constraint remains the final defensive invariant. Different conversations use different row locks and can still proceed concurrently.

An order-verification unit test now requires `findByIdForUpdate` before membership and sequence reads, preventing accidental regression of this critical ordering.

## Alternative approaches considered

### Option A — lock before any consistent read

Selected. It fixes the verified MySQL snapshot ordering problem, requires no schema migration, preserves all APIs, and is the smallest change. No normal database `SELECT` occurs in `send` before the lock; request validation is in-memory.

### Option B — explicit `Conversation.nextMessageSequence`

This would allocate and increment a counter on the locked conversation row. It avoids `MAX(sequence) + 1` and makes allocation intent explicit, but requires a schema migration, backfilling existing conversations, lifecycle rules, and additional recovery/testing. It would not remove same-conversation row contention. Because Option A is demonstrably correct, Option B was not implemented.

Retries after duplicate-key errors, Redis locks, Kafka, queues, and application-local mutexes were rejected: they either mask incorrect allocation, add infrastructure, or do not coordinate multiple instances reliably.

## MySQL concurrency regression test

`MessageSequenceMySqlConcurrencyIntegrationTest` starts MySQL 8.4 with Testcontainers and calls the real proxied `MessageService.send`, so every worker gets its own Spring transaction. A latch releases 10 or 25 executor threads together against one conversation.

For each level the test verifies:

- every future completes successfully;
- persisted count equals requested sends;
- distinct sequence count equals persisted count;
- minimum sequence is 1 and maximum is N;
- every value from 1 through N exists.

The old order reproduces duplicate-key failures in the standalone MySQL benchmark. With the selected fix, both MySQL regression cases pass.

## Benchmark before vs after

Each row is the average of three runs; every run uses a fresh conversation. Counts are per run.

| Concurrent sends | Before success | Before failed | After success | After failed | After persisted | Duplicates | Missing | After p95 ms | After req/s | After error % |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1 | 9 | 10 | 0 | 10 | 0 | 0 | 210.68 | 44.99 | 0% |
| 25 | 3 | 22 | 25 | 0 | 25 | 0 | 0 | 438.12 | 60.91 | 0% |
| 50 | 5.67 | 44.33 | 50 | 0 | 50 | 0 | 0 | 969.52 | 52.62 | 0% |
| 100 | 10 | 90 | 100 | 0 | 100 | 0 | 0 | 1,299.90 | 72.50 | 0% |

Raw baseline and fixed-run artifacts are retained separately in `performance/concurrent-message-send/results/before` and `results/after`.

## Correctness results

All 12 fixed-code benchmark runs passed:

- 3/3 at 10 sends persisted sequences 1–10.
- 3/3 at 25 sends persisted sequences 1–25.
- 3/3 at 50 sends persisted sequences 1–50.
- 3/3 at 100 sends persisted sequences 1–100.
- 0 failed requests, 0 duplicate persisted sequences, and 0 missing sequences.
- All console failure logs were empty.

The fix therefore supports the required positive correctness claim up to the tested maximum of 100 simultaneous same-conversation sends in this local environment.

## Performance results

| Concurrent sends | p50 ms | p95 ms | p99 ms | Average ms | Req/s | Average InnoDB lock-wait delta ms |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 106.79 | 210.68 | 218.50 | 114.08 | 44.99 | 821.33 |
| 25 | 233.76 | 438.12 | 455.44 | 235.47 | 60.91 | 3,184.67 |
| 50 | 566.72 | 969.52 | 1,004.89 | 545.22 | 52.62 | 8,208.33 |
| 100 | 911.77 | 1,299.90 | 1,331.85 | 812.31 | 72.50 | 11,548.00 |

Latency and accumulated lock-wait time rise with concurrency because writes to one conversation intentionally serialize. That is the correctness trade-off, not evidence that the fix failed. Short burst throughput varies with scheduling and should not be treated as production capacity.

## Trade-offs

- Same-conversation sends wait for one row lock, increasing p95/p99 at higher concurrency.
- The lock now covers membership validation and sender lookup as well as allocation, slightly lengthening the critical section.
- Different conversations remain independent because they lock different conversation rows.
- The approach has no migration and minimal operational complexity.
- An explicit counter may be worth reconsidering only if profiling later shows the latest-message query is material; it would not eliminate serialization required by contiguous ordering.

## Regression results

- Targeted MySQL 8.4 regression: **2/2 passed** (10 and 25 concurrent sends).
- Full backend suite: **44 tests passed**, 0 failures, 0 errors, 0 skipped.
- System Playwright E2E: **4/4 passed** using the backend from this branch.
- Existing k6 chat API smoke test: **passed** with all configured thresholds.
- Fixed-code concurrency benchmark: **12/12 runs passed correctness checks**.

Testcontainers emits harmless shutdown warnings after its MySQL container closes; they do not represent test failures.

## Limitations

- Local Docker benchmark on one laptop; not a production SLA or capacity test.
- One Spring Boot instance, one authenticated user, text-only payloads, and one conversation per run.
- Not a distributed multi-instance benchmark.
- Maximum tested concurrency was 100, as scoped.
- MySQL global lock-wait status is supporting evidence, not per-transaction tracing.
- Different-conversation concurrency was reasoned from row-level lock scope but was not benchmarked in this task.

## CV-safe claim

> Validated ordered message creation under 100 concurrent same-conversation sends with zero failed requests and zero duplicate or missing sequences using transaction-level pessimistic locking and a database uniqueness constraint in a local MySQL/k6 benchmark.

Keep “local MySQL/k6 benchmark” in the claim; do not present this as production capacity.

## What I should learn

- Transaction correctness depends on both the lock and the order of reads inside the transaction.
- MySQL `REPEATABLE READ` normal selects use a consistent snapshot; locking reads use current-read semantics.
- A pessimistic lock acquired after a snapshot does not automatically make later normal reads observe newer commits.
- Same-conversation contiguous sequences require serialization somewhere.
- A unique constraint protects stored data but is not a substitute for correct allocation because rejected valid writes are still application failures.
- p95/p99 rise under lock contention because later requests wait behind earlier transactions.
- Row-level locking preserves concurrency across different conversations, unlike a global lock.
- A MySQL regression test was essential because H2 alone did not reproduce the isolation behavior.

## 30-second Vietnamese interview answer

> Tôi benchmark việc gửi đồng thời và phát hiện dù đã có pessimistic lock, khoảng 90% request vẫn lỗi duplicate sequence. Nguyên nhân là membership query chạy trước lock, tạo snapshot `REPEATABLE READ`; sau khi chờ lock, query lấy sequence vẫn có thể đọc snapshot cũ. Tôi sửa tối thiểu bằng cách khóa conversation trước mọi database read, giữ unique constraint làm lớp bảo vệ cuối. Testcontainers MySQL với 10 và 25 thread đều pass, và benchmark k6 ba lần ở mức 10, 25, 50, 100 đều thành công 100%, không trùng hay thiếu sequence. Đổi lại, latency tăng theo contention vì cùng conversation phải serialize.

## 30-second English interview answer

> My concurrency benchmark showed that having a pessimistic lock was not enough: roughly 90% of sends failed with duplicate sequences. Membership was read before the lock, establishing a MySQL repeatable-read snapshot, so a transaction could wait for the lock and still read a stale maximum sequence. I fixed it by locking the conversation before any database read and kept the unique constraint as a final safeguard. MySQL Testcontainers tests at 10 and 25 threads passed, and three k6 runs at 10, 25, 50, and 100 concurrent sends all had zero failures, duplicates, or gaps. Higher latency is the expected cost of same-conversation serialization.
