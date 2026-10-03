# Correctness Fix Report

# 1. Refresh-token issue

## Root cause

The issue was real. `AuthService.refreshToken()` starts a transaction and calls `RefreshTokenService.verifyForRotation()`. The latter also used the default `REQUIRED` propagation, so it joined the same transaction.

When a token already revoked as `ROTATED` was reused, the old implementation called `RefreshTokenRepository.revokeActiveByFamilyId(...)` and immediately threw `AppException(TOKEN_REUSE_DETECTED)`. Because `AppException` is a runtime exception, it marked the shared transaction for rollback. The error response was correct, but the family revocation update could be rolled back with that transaction. The previously issued replacement token could therefore remain usable.

The existing unit test verified only that the repository method was invoked. The existing API integration test verified only that reuse returned an error. Neither test queried committed database state or attempted to use the replacement token afterward.

## Files changed

- `src/main/java/com/tranverse/chatserver/service/RefreshTokenService.java`
  - `verifyForRotation(...)` now delegates reuse revocation to a dedicated service.
- `src/main/java/com/tranverse/chatserver/service/RefreshTokenFamilyRevocationService.java`
  - Added `revokeActiveFamilyForReuse(...)` with `Propagation.REQUIRES_NEW`.
- `src/test/java/com/tranverse/chatserver/service/RefreshTokenServiceTest.java`
  - Updated the unit test to verify delegation to the durable revocation boundary.
- `src/test/java/com/tranverse/chatserver/integration/AuthenticationFlowIntegrationTest.java`
  - Strengthened the rotation/reuse test to inspect committed rows and retry the replacement token.

## Fix

The family-revocation write now executes through a separate Spring bean in a `REQUIRES_NEW` transaction. A separate bean is necessary because Spring transaction propagation is proxy-based; calling a `REQUIRES_NEW` method through self-invocation in `RefreshTokenService` would not activate the new transaction.

Before:

```text
reuse detected
→ revoke active family in the request transaction
→ throw AppException
→ request transaction rolls back
→ family revocation may be lost
```

After:

```text
reuse detected
→ suspend request transaction
→ revoke active family in REQUIRES_NEW transaction
→ commit security revocation
→ resume request transaction
→ throw AppException
→ return error while family remains revoked
```

The exception is preserved, so the public API behavior remains unchanged.

## Tests

`AuthenticationFlowIntegrationTest.refreshRotatesTokenAndRejectsReuseOfPreviousToken` now performs this complete flow:

1. Login and obtain token A.
2. Refresh A and obtain token B.
3. Reuse A and assert `TOKEN_REUSE_DETECTED`.
4. Query `RefreshTokenRepository` after the failed HTTP request.
5. Assert both persisted family tokens are revoked.
6. Attempt to refresh B and assert `AUTH_INVALID_TOKEN`.

The integration test is no longer wrapped in one test-managed transaction; each MockMvc request commits or rolls back using application transaction boundaries. H2 is sufficient for verifying Spring rollback/`REQUIRES_NEW` propagation and committed JPA state in this focused test. A MySQL/Testcontainers test was not added because no database-specific SQL or isolation behavior is involved in this fix.

---

# 2. Read-receipt concurrency issue

## Root cause

The issue was real. `MessageService.markRead(...)` loaded a `ConversationMember`, compared the requested message sequence with the entity's current `lastReadMessage.sequence` in Java, then updated the managed entity.

Two transactions could both read the same old watermark. For example, one could decide to write sequence 100 while another decided to write sequence 80. Without a lock, version column, or conditional database update, a later commit of 80 could overwrite 100. The Java comparison protected sequential calls but did not make the read-modify-write operation atomic.

## Files changed

- `src/main/java/com/tranverse/chatserver/repository/ConversationMemberRepository.java`
  - Added `advanceLastReadIfNewer(...)`, an atomic conditional update.
- `src/main/java/com/tranverse/chatserver/service/ConversationService.java`
  - Added the transactional repository wrapper `advanceLastReadIfNewer(...)`.
- `src/main/java/com/tranverse/chatserver/service/MessageService.java`
  - Replaced the entity read/Java comparison with the atomic update result.
- `src/test/java/com/tranverse/chatserver/service/MessageServiceTest.java`
  - Verifies publishing occurs only when the watermark advances.
- `src/test/java/com/tranverse/chatserver/integration/ReadReceiptConcurrencyIntegrationTest.java`
  - Added persisted sequential, duplicate, and two-thread concurrency cases.

## Fix

The repository now performs one conditional database update equivalent to:

```text
UPDATE conversation_member
SET last_read_message = requestedMessage
WHERE member = currentMember
  AND (last_read_message IS NULL
       OR currentSequence < requestedSequence)
```

The actual JPQL uses a correlated `EXISTS` query to compare the current message sequence. The database evaluates the predicate and performs the row update atomically. Concurrent updates serialize at the affected row, and an older request cannot satisfy the predicate after a newer watermark is stored.

The repository returns the affected-row count. A read event is published only when exactly one row advances, preserving the REST/STOMP contract while avoiding redundant events for duplicate or older marks.

No schema change, public DTO change, pessimistic lock held across unrelated work, or application-only `Math.max` was introduced.

## Tests

`ReadReceiptConcurrencyIntegrationTest` verifies committed database state for:

- `marking50Then100EndsAt100`
- `marking100Then50DoesNotRegress`
- `duplicateMarkReadIsIdempotent`
- `concurrentUpdatesCannotMoveWatermarkBackwards`

The concurrency test releases two executor threads together. Each thread enters `MessageService.markRead(...)` in its own Spring transaction and attempts to persist sequence 50 or 100. After both complete, the stored foreign key points to message 100.

The test uses H2 in MySQL compatibility mode. It executes the same JPQL bulk update and verifies the database invariant. Production MySQL also provides atomic row-update predicate evaluation. No MySQL-only syntax was introduced.

---

# 3. Regression results

## Before the change

- Static transaction inspection confirmed that reuse revocation joined the failing outer transaction.
- Static read/write inspection confirmed that the watermark update was an unlocked read-modify-write operation.
- The pre-existing tests did not assert either required persisted-state invariant.
- A separate pre-fix test run was not preserved; no claim is made that a red test artifact was recorded before implementation.

## After the change

- Focused refresh-token suite: **10 tests passed**, 0 failures, 0 errors.
  - `RefreshTokenServiceTest`
  - `AuthenticationFlowIntegrationTest`
- Focused read-receipt suite: **11 tests passed**, 0 failures, 0 errors.
  - `MessageServiceTest`
  - `ReadReceiptConcurrencyIntegrationTest`
- Full backend suite: **42 tests passed**, 0 failures, 0 errors, 0 skipped.
- Command: `.\mvnw.cmd test`
- Backend E2E: not run. This backend repository contains no E2E suite; the system Playwright suite lives in the separate parent/meta repository and does not build this working branch automatically. This is reported rather than claiming an unrelated E2E run.

During test development, the first read-receipt query failed on a null watermark because relationship path navigation introduced join semantics. It was corrected to a correlated `EXISTS` predicate, then all focused and full tests passed.

---

# 4. What I should learn from these fixes

## Transaction rollback

A transaction groups writes into one all-or-nothing unit. If a runtime exception escapes a Spring `@Transactional` method, Spring normally rolls back every write in that transaction. In this project, that meant “revoke the family, then throw reuse error” could undo the revocation.

## Transaction propagation

Propagation controls how a transactional method relates to an existing transaction. `REQUIRED` joins the caller's transaction. `REQUIRES_NEW` suspends it and creates an independent transaction. The reuse revocation uses `REQUIRES_NEW` so its security write commits even though the refresh request subsequently fails.

Spring applies propagation through proxies. That is why the new transaction method is in `RefreshTokenFamilyRevocationService`, a separate injected bean, rather than a method called internally on `this`.

## Why a security side effect sometimes must survive a failed request

Most writes should roll back when a request fails. Reuse detection is different: detecting a stolen/replayed credential is security evidence, and revoking its family is the protective response. The client must receive an error, but the protection must remain committed.

## Race condition

A race condition occurs when the result depends on thread timing. Two mark-read requests can execute in either order. The required result must always be the highest sequence, regardless of that timing.

## Lost update

A lost update occurs when two transactions read the same old value, compute separate new values, and one overwrites the other. The old `markRead` implementation could let sequence 80 overwrite sequence 100 even though both Java comparisons had been correct when executed.

## Pessimistic versus optimistic locking

- A pessimistic lock blocks other writers while a row is held. It is simple but can increase waiting and deadlock risk.
- Optimistic locking adds a version and rejects a stale write, requiring retry handling.

Neither was necessary here. The invariant can be expressed as one conditional database update, which is smaller and does not add retry behavior or a schema column.

## Atomic conditional update

An atomic conditional update combines comparison and write in one database statement. For this project, the row changes only when the requested message sequence is newer. If another transaction has already stored 100, an attempt to store 80 affects zero rows.

## Database constraint/invariant

An invariant is a rule that must remain true for every committed state. Here the invariant is monotonicity: `newLastReadSequence >= oldLastReadSequence`. The conditional update enforces it at the point where concurrent writes are serialized: the database.

## Why application `Math.max` is insufficient

`Math.max(current, requested)` is correct only for the values visible to one thread. Two threads can both calculate from a stale `current` value and still overwrite each other. The compare and write must be protected by a lock/version check or performed atomically by the database.

---

# 5. Interview explanation

## 30-second Vietnamese answer

> Trong chat app, tôi xử lý hai lỗi transaction và concurrency. Khi phát hiện refresh token cũ bị reuse, thao tác revoke cả token family trước đây có thể bị rollback cùng exception. Tôi tách revoke sang transaction `REQUIRES_NEW` và viết integration test kiểm tra dữ liệu đã commit, đồng thời token mới cũng không refresh được. Với read receipt, cách đọc rồi cập nhật entity có thể làm watermark từ 100 lùi về 80 khi hai request chạy đồng thời. Tôi thay bằng atomic conditional update tại database và test bằng hai transaction chạy trên hai thread.

## 30-second English answer

> I fixed two transaction and concurrency issues in my chat application. Refresh-token reuse detection previously revoked the token family and then threw an exception in the same transaction, so the security revocation could roll back. I moved that revocation into a `REQUIRES_NEW` transaction and verified committed database state and rejection of the replacement token. For read receipts, an entity read-modify-write could regress the watermark from 100 to 80 under concurrent requests. I replaced it with an atomic conditional database update and verified it with two concurrent transactions.

---

## Git summary

- **Base branch:** `main` at `146500100f5bbbbcd21a2070eebcb922ef4e7fe9` (fast-forwarded to the current `origin/main` before branching).
- **Working branch:** `fix/chat-correctness-issues`.
- **Files changed:**
  - `src/main/java/com/tranverse/chatserver/service/RefreshTokenService.java`
  - `src/main/java/com/tranverse/chatserver/service/RefreshTokenFamilyRevocationService.java`
  - `src/main/java/com/tranverse/chatserver/repository/ConversationMemberRepository.java`
  - `src/main/java/com/tranverse/chatserver/service/ConversationService.java`
  - `src/main/java/com/tranverse/chatserver/service/MessageService.java`
  - `src/test/java/com/tranverse/chatserver/service/RefreshTokenServiceTest.java`
  - `src/test/java/com/tranverse/chatserver/service/MessageServiceTest.java`
  - `src/test/java/com/tranverse/chatserver/integration/AuthenticationFlowIntegrationTest.java`
  - `src/test/java/com/tranverse/chatserver/integration/ReadReceiptConcurrencyIntegrationTest.java`
  - `CORRECTNESS_FIX_REPORT.md`
- **Commits created:**
  - `fix(auth): make refresh token family revocation durable`
  - `fix(chat): prevent read receipt watermark regression`
  - `docs: add correctness fix report`
- **Pushed:** No.
- **Main modified by fix work:** No. Local `main` was only fast-forwarded to `origin/main` before the branch was created; all correctness changes and commits were made on `fix/chat-correctness-issues`.
