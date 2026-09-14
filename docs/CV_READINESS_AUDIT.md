# CV-readiness audit

Audit date: 2026-09-14

## Verified baseline

Executable source and automated checks are the source of truth for this document.

- Backend: Java 21, Spring Boot 4, Spring Security, JPA, MySQL, Redis, STOMP/WebSocket,
  Google OAuth2, and Cloudinary.
- Frontend: React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, Axios, STOMP.js,
  and SockJS.
- Architecture: a modular-monolith backend process and a separate React SPA.
- Automated checks: 18 backend tests and 23 frontend unit/component tests; frontend
  lint and optimized build also pass.
- Runtime status: local execution and container packaging are documented; no public
  hosting is claimed.

## Verified capabilities

- Email registration and password recovery with Redis-backed OTP throttling.
- JWT access tokens, rotating refresh tokens, token-family reuse detection, current-
  session logout, and all-session logout.
- Google OAuth2 callback with a short-lived, single-use exchange code.
- User profile update and user search.
- Unique private conversations and group conversations with owner, admin, and member
  roles.
- Membership changes, ownership transfer, expiring invitation links, and optional
  administrator review for join requests.
- Text and multi-image messages, replies, editing, soft deletion, cursor history,
  read receipts, and typing events.
- Authenticated multipart image upload through the backend to Cloudinary.
- Authenticated STOMP CONNECT, membership checks on SUBSCRIBE, and service-level
  authorization for message operations.
- Message lifecycle, read, and typing events published after transaction commit.
- Dockerfiles, local Docker Compose configuration, and independent backend/frontend
  CI workflows.

## Explicit limitations

- Public hosting is outside the verified project scope.
- Online/offline presence, reactions, pinned messages, calls, push notifications,
  message search, and selected-session revocation are not implemented.
- There is no cross-repository browser E2E suite or verified one-command full-stack
  Compose environment.
- Tests use H2 for the automated backend baseline; MySQL runtime compatibility is not
  a substitute for a dedicated Testcontainers integration suite.
- The schema has targeted Flyway repair migrations but no complete baseline migration
  for a fresh database. Hibernate schema update remains enabled for local MVP use.
- The built-in STOMP simple broker targets a single backend instance; horizontal
  scaling would require shared broker and state infrastructure.

Only the verified capabilities above should be used as CV claims.
