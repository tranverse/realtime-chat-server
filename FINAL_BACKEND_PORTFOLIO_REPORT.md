# Final Backend Portfolio Report

## Git status

- Backend repository: `realtime-chat-server`
- Concurrency fix base: `main`
- Fix commit range: `a419a8f` through `f096bd3`
- Documentation branch: `docs/backend-benchmark-results`
- Documentation branch is intentionally not merged or pushed.

## Fix merged into main

`fix/concurrent-message-sequence` was fast-forward merged into `main` without squashing or rewriting history. `main` was pushed normally to `origin/main` at `f096bd3`.

The only production behavior change moves the existing conversation `PESSIMISTIC_WRITE` lock before database-backed membership, user, and sequence reads in `MessageService.send`. Public REST/STOMP contracts, `max(sequence) + 1` allocation, and the `(conversation_id, sequence)` unique constraint remain unchanged.

## Final test results

- Pre-merge backend suite: **44 passed**, 0 failures/errors/skips.
- Post-merge backend suite on `main`: **44 passed**, 0 failures/errors/skips.
- MySQL 8.4 Testcontainers concurrency regression: **2 passed** at 10 and 25 concurrent sends.
- Playwright system E2E: **4/4 passed**.
- Existing k6 chat API smoke test: **passed** with configured thresholds.
- Concurrent-send benchmark: **12/12 runs passed** correctness checks.

## Message-history benchmark summary

The local controlled benchmark used MySQL 8.4, k6, 10 concurrent users, a page size of 50, three repetitions, and datasets of 10K, 100K, and 500K messages. At 500K messages and 90% depth:

- Keyset pagination p95: **17.23 ms**
- Offset pagination p95: **4,380.11 ms**
- Local measured p95 reduction: **99.61%**
- Error rate: **0%**

MySQL used the `(conversation_id, sequence)` index. Keyset could seek by conversation and sequence range, while deep offset had to traverse and discard earlier entries. This is a local comparison, not production capacity or an SLA.

## Concurrent-message benchmark summary

The local benchmark used real authenticated message POST requests, MySQL 8.4, k6, one target conversation per run, and three repetitions at each level.

| Concurrent same-conversation sends | Success | Failed | Duplicate sequences | Missing sequences | p95 |
|---:|---:|---:|---:|---:|---:|
| 10 | 10 | 0 | 0 | 0 | 210.68 ms |
| 25 | 25 | 0 | 0 | 0 | 438.12 ms |
| 50 | 50 | 0 | 0 | 0 | 969.52 ms |
| 100 | 100 | 0 | 0 | 0 | 1,299.90 ms |

Before the fix, 88–90% of concurrent requests failed because a membership read could establish a MySQL `REPEATABLE READ` snapshot before the conversation lock, allowing a stale latest-sequence read. After moving the lock first, every measured request succeeded and sequences remained unique and contiguous. Higher latency at greater concurrency is the expected cost of serializing writes to one conversation row; different conversations retain independent row-level concurrency.

These figures apply only to a local single-instance benchmark and do not establish distributed scalability or production capacity.

## README changes

The README now documents:

- pagination benchmark environment, dataset sizes, and the 500K deep-page comparison;
- concurrent-send levels and verified correctness at 100 simultaneous same-conversation sends;
- the transaction timeline before and after the sequence fix;
- explicit local-benchmark and non-SLA limitations;
- MySQL Testcontainers coverage in the backend test suite.

No production code was changed on the documentation branch.

## CV-safe claims

- Validated sequence-based keyset pagination across up to 500K messages, achieving ~17 ms p95 versus ~4.38 s for deep offset pagination in a local MySQL benchmark.
- Validated ordered message creation under 100 concurrent same-conversation sends with zero failures, duplicate sequences, or missing sequences using per-conversation pessimistic locking and database constraints.

These claims must retain their tested scope. They do not imply production SLA, global concurrent-user capacity, distributed scalability, or multi-instance WebSocket scalability.

## 30-second Vietnamese project explanation

> Tôi xây dựng backend chat realtime theo modular monolith với Spring Boot, MySQL, Redis và STOMP/WebSocket. Tôi không chỉ hoàn thiện feature mà còn kiểm chứng hai quyết định backend bằng benchmark local. Với lịch sử 500 nghìn message, keyset pagination đạt p95 khoảng 17 ms so với 4,38 giây của deep offset. Benchmark gửi đồng thời còn phát hiện lỗi snapshot trước pessimistic lock; tôi đổi thứ tự để khóa conversation trước mọi database read. Sau fix, ba lượt test ở mức 100 send cùng conversation đều thành công, không trùng hoặc thiếu sequence. Đây là kết quả local, không phải production SLA.

## 30-second English project explanation

> I built a realtime chat backend as a Spring Boot modular monolith using MySQL, Redis, and STOMP/WebSocket. I also validated two backend design decisions with controlled local benchmarks. At 500K messages, keyset pagination achieved about 17 milliseconds p95 versus 4.38 seconds for deep offset pagination. A concurrent-send benchmark exposed a repeatable-read snapshot issue before the pessimistic lock, so I reordered the transaction to lock the conversation before any database read. After the fix, three runs at 100 same-conversation sends completed with no failures, duplicates, or sequence gaps. These are local validation results, not a production SLA.
