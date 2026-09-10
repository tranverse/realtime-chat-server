# CV-readiness audit

Audit date: 2026-09-10

## Scope and verified baseline

This audit covers the Spring Boot backend in this repository and the React client in
`tranverse/realtime-chat-client`. It treats executable source and automated checks as
the source of truth.

- Backend: Java 21, Spring Boot 4, Spring Security, JPA, MySQL, Redis and STOMP.
- Frontend: React 19, TypeScript, Vite, TanStack Query, Axios, STOMP.js and SockJS.
- Architecture: one modular-monolith backend deployment with a separate static SPA.
- Baseline backend tests: 11 passing.
- Baseline frontend checks: lint passing, 5 tests passing and production build passing.

The supplied brief described PostgreSQL, but the implementation, Maven dependency,
Compose service and documentation consistently use MySQL. A database migration is
not assumed to be complete and must not be claimed on a CV.

## Verified capabilities

- Email registration and password recovery with OTP throttling in Redis.
- JWT access tokens, rotating refresh tokens, token-family reuse detection, logout
  for the current session and logout for all sessions.
- Google OAuth2 callback flow.
- Direct-conversation uniqueness through a normalized database key.
- Group ownership, administrator/member roles, membership changes, invitation links
  and join-request review.
- Message sequence allocation under a pessimistic conversation-row lock plus a
  unique `(conversation_id, sequence)` constraint.
- Cursor-like history queries using `beforeSequence`.
- Authenticated STOMP CONNECT, conversation-membership checks on SUBSCRIBE and
  service-level authorization on SEND operations.
- Message, edit, delete, read and typing events are published after transaction commit.
- Nginx SPA fallback and WebSocket proxy exist in the frontend image.
- Independent backend and frontend CI workflows exist.

## P0 findings

| Area | Finding | Risk | Required verification |
| --- | --- | --- | --- |
| Soft delete | History excludes deleted rows, so the deleted placeholder disappears after reload and deleted reply targets cannot be represented consistently. | Message timelines change after refresh. | Repository/service tests plus REST history test. |
| Read receipts | The backend publishes `MESSAGES_READ`, but the frontend cache and message UI do not store or render receipt progress. | Required feature is only partially implemented. | Two-user UI/state test. |
| History scrolling | The active chat scrolls to the bottom whenever query fetching changes, including when older pages are loaded. | Users lose their position while reading history. | DOM scroll preservation test. |
| Reconnect recovery | STOMP reconnect restores subscriptions, but the client does not refetch authoritative state after reconnect. | Events sent during the outage may remain missing. | Reconnect integration/state test. |
| Realtime tests | Current frontend tests cover cache edit/delete only; backend tests do not prove CONNECT/SUBSCRIBE denial. | Duplicate delivery and authorization regressions can pass CI. | Focused socket and reconciliation tests. |
| Rate limiting | OTP limits are hard-coded and use multiple non-atomic Redis operations; failure policy and Retry-After behavior are undocumented. | Configuration and failure behavior are unclear. | Configurable policy and Redis-backed tests. |
| Full-stack Docker | Backend and frontend images exist separately, but no root Compose file starts frontend, backend, database and Redis as one system. | The documented one-command demo is unavailable. | Compose config, image build and health checks. |

## P1 findings

- Multi-device sessions exist at the data model/token-family level, but there is no
  list-sessions or revoke-selected-session API/UI. Current CV wording must be limited
  to current-session and all-session revocation.
- Online/offline presence is not implemented and must not be claimed.
- Conversation list updates after message events through query invalidation, but its
  realtime reordering and unread behavior require an E2E assertion.
- Database query counts and N+1 behavior have not been measured for conversation
  detail/list and reply previews.

## Engineering and delivery findings

- Existing feature history is understandable, but both repositories need a `develop`
  integration branch for the stabilization work requested in the brief.
- Production schema management still relies on Hibernate configuration. A migration
  tool is a production-awareness improvement, not a prerequisite for the first P0 fixes.
- CI verifies unit/application-context checks but does not currently validate a full
  Compose environment or cross-repository E2E scenario.
- The README documents the main architecture well but needs a verified system-level
  Docker command, explicit reconnect behavior and a final limitations matrix.

## Stabilization plan

1. Bootstrap `develop` in both repositories and branch every logical change from it.
2. Preserve deleted messages safely in history and add lifecycle tests.
3. implement frontend receipt state, scroll preservation, reconnect resync and
   canonical ID reconciliation tests.
4. Add WebSocket authentication/subscription tests and REST non-member tests.
5. Make Redis rate-limit policy configurable and test 429/window behavior.
6. Add concurrency and stable-cursor integration tests against the production database
   engine where practical.
7. Add a root full-stack Compose definition and verify Nginx REST/WebSocket proxying.
8. Extend CI and README only with behavior demonstrated by automated checks.

## Claims not yet approved

Until the relevant work is verified, do not claim online presence, selected-session
revocation, PostgreSQL, complete read receipts, reconnect gap recovery, full E2E test
coverage or one-command full-stack deployment.
