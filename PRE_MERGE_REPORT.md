# Pre-Merge Report

## Backend tests

- Command: `.\mvnw.cmd test`
- Result: **PASS**
- Tests: **42 passed, 0 failed, 0 errors, 0 skipped**
- Includes persisted refresh-token family revocation coverage and sequential/duplicate/two-thread read-watermark coverage.

## System E2E

- Environment: Docker Compose with MySQL 8.4, Redis 7.4, Mailpit, backend, frontend, and Playwright.
- The backend image was built from the current `fix/chat-correctness-issues` checkout; the existing frontend and system tests came from the parent `realtime-chat-application` repository.
- Playwright result: **PASS — 4/4 tests** in 33.2 seconds.
- Covered session restore, login validation, private realtime messaging/reply/edit/delete/read receipts, and group messaging/details/confirmed leave.
- Docker environment and test volumes were removed after the run.

## k6 smoke test

- Result: **PASS**
- Scenario: existing read-path smoke test, up to 10 virtual users for 20 seconds.
- Checks: **6675/6675 passed (100%)**.
- HTTP failures: **0/6676 (0.00%)**.
- HTTP duration: average **22.55 ms**, p95 **54.85 ms**, maximum **187.49 ms**.
- Throughput observed in this local run: **331.51 HTTP requests/second**.
- All existing thresholds passed. These are local smoke-test results, not production capacity claims.

## Files changed against `main`

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
- `PRE_MERGE_REPORT.md`

## Scope review

Review of `git diff main...HEAD` found only:

1. Durable refresh-token family revocation after reuse detection.
2. Monotonic read-receipt watermark updates using an atomic conditional database update.
3. Unit/integration regression tests for those changes.
4. Correctness and pre-merge documentation.

No public API contract, unrelated feature, infrastructure definition, or frontend production code was changed.

## Regressions and merge assessment

- Regressions found: **None**.
- Diff check: **PASS**; no whitespace errors.
- Working branch: `fix/chat-correctness-issues`.
- Pushed: **No**.
- Merged into `main`: **No**.
- Assessment: **Safe for manual review and merge based on the full backend suite, Docker/Playwright system E2E, k6 smoke test, and scope review.**
