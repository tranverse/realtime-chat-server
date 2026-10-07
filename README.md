# Realtime Chat Server

Backend for private and group chat, built with Java 21 and Spring Boot 4.0.6. REST APIs handle account, conversation, history and media operations; WebSocket/STOMP delivers messages, typing, read receipts and presence changes.

This is a **modular monolith**: one Spring Boot application and one business transaction boundary, with responsibility-based services. Modules are logical boundaries within a layered codebase, not independently deployed services or a strict Clean Architecture implementation.

Related repositories: [React client](https://github.com/tranverse/realtime-chat-client) · [full system and E2E harness](https://github.com/tranverse/realtime-chat-application).

## Features

- Authentication: email/OTP registration and resend, email/password login, password recovery, Google OAuth2, access/refresh JWTs, refresh rotation/reuse detection, logout and logout-all. Each login creates a refresh-token family for independent sessions; no device-enumeration API is provided.
- Conversations: direct chats, groups, member management, OWNER/ADMIN/MEMBER permissions, ownership transfer, expiring/revocable invite links and approval-based join requests.
- Messaging: text and image attachments, replies, sender-only edits, permission-checked soft deletion, typing, monotonic read receipts and sequence-based history. REST history supports frontend reconciliation after reconnect, not durable event replay.
- Media: authenticated image upload to Cloudinary; profile/group avatars use uploaded image URLs. JPEG, PNG, WebP and GIF are accepted with configurable size limits.
- Presence: real Redis-backed Online/Offline state per authenticated STOMP session, including multiple tabs/devices, heartbeat, clean disconnect and silent expiry.

## Technology

| Area | Implementation |
| --- | --- |
| Runtime | Java 21; Spring Boot 4.0.6 |
| HTTP/security | Spring MVC, Spring Security, OAuth2 client/resource server, Bean Validation |
| Persistence | Spring Data JPA/Hibernate, MySQL; H2 for selected tests |
| Ephemeral state | Spring Data Redis; Redis 7.4 in Compose/tests |
| Realtime | Spring WebSocket/STOMP, native WebSocket and SockJS endpoint |
| Schema | Flyway core/MySQL; current default also uses Hibernate `ddl-auto=update` |
| Integrations | Cloudinary Java SDK 2.4.0, SMTP mail, Google OAuth2 |
| API documentation | springdoc OpenAPI 3.0.3 |
| Testing | JUnit/Spring Boot Test, Mockito, Testcontainers 1.21.3 |
| Containers | Java 21 Docker image, Docker Compose with MySQL 8.4/Redis 7.4 |

Spring dependency versions are managed by the Spring Boot parent in `pom.xml`; they are not independently pinned here.

## System architecture

```text
                         React / TypeScript client
                                   |
                    +--------------+--------------+
                    |                             |
              REST /api/v1/**                STOMP /ws
                    |                             |
                    +--------------+--------------+
                                   |
                     Spring Boot modular monolith
                Security -> Controllers -> Services
                                   |
          +-----------+------------+-----------+------------+
          |           |            |           |            |
        Auth        Users    Conversations  Messaging   Media / Presence
          |           |            |           |            |
          +-----------+------------+-----------+------------+
                                   |
             +---------------------+---------------------+
             |                     |                     |
           MySQL                 Redis               Cloudinary
        durable data       OTP / OAuth / presence     image bytes

           Google OAuth2 and SMTP integrate with authentication.
```

| Responsibility | Boundary |
| --- | --- |
| Authentication | Registration/OTP, login, Google callback/exchange, JWT issuance, refresh families, reset and revocation |
| Users | Profile/account data and user search |
| Conversations | Direct/group lifecycle, membership, roles, ownership, invites and join requests |
| Messaging | Message validation, sequence allocation, history, replies, edit/delete, typing and read state |
| Media | Image type/size validation and Cloudinary upload; MySQL stores attachment metadata |
| Presence | Authenticated socket lifecycle and atomic Redis session state; no user-table online flag |

### REST request flow

```text
Client -> Spring Security -> Controller -> Service -> Repository -> MySQL
                                             |
                                             +-> Redis / external integrations
```

Controllers handle transport, validation and API envelopes. Services enforce business rules and resource-level access within transactions. Repositories provide persistence queries. Security verifies bearer access JWTs; a valid token alone does not grant access to another user's conversation.

## Authentication architecture

Access JWTs use HS256, issuer `tranverse`, a user-ID subject, expiry, token type and authorities. REST bearer authentication and STOMP CONNECT share the access-token decoder. Lifetimes are configured through environment variables; `.env.example` uses 15-minute access tokens and 30-day refresh tokens.

Refresh JWTs use a separate signing key. Only their SHA-256 hashes, family IDs, expiry and revocation state are stored in MySQL. Refresh obtains a pessimistic lock on the stored token, revokes it as ROTATED and issues a replacement in the same family. Reuse of a rotated token revokes the active family through a separate Spring bean with `REQUIRES_NEW`; the revocation commits even when the outer request returns an authentication exception.

Logout revokes the supplied refresh token. Logout-all and password reset revoke the user's refresh tokens. These operations do **not** immediately invalidate already-issued access JWTs or forcibly disconnect another active socket; short-lived access expiry remains the boundary. The client disconnects its own transport on sign-out.

```text
Google authorization -> backend /login/oauth2/code/google callback
                     -> application JWTs
                     -> Redis single-use code (60-second TTL)
                     -> frontend callback with code
                     -> POST /api/v1/auth/oauth2/exchange -> JWT response
```

Exchange uses Redis `GETDEL`; the code is consumed once. Tokens are not placed directly in the OAuth callback URL. SMTP delivers registration/recovery codes. Redis send limits are 5 requests/email per 15 minutes and 20/IP, with a 60-second resend cooldown; verify limits are 5/email and 30/IP per 5 minutes, scoped by OTP purpose.

## WebSocket / STOMP architecture

The HTTP handshake is `/ws` with configured origins. JWT authentication happens on STOMP CONNECT through the `Authorization: Bearer ...` header, not a separate socket identity model.

| Destination | Direction / behavior |
| --- | --- |
| `/app/conversations/{id}/messages` | Client SEND; delegates to the same message service as REST |
| `/app/conversations/{id}/typing` | Client SEND; active-member validation, ephemeral typing broadcast |
| `/app/conversations/{id}/read` | Client SEND; membership/message validation and conditional watermark update |
| `/topic/conversations/{id}` | SUBSCRIBE requires active membership; created/updated/deleted/read/typing events |
| `/app/presence/heartbeat` | Client SEND; identity/session taken from authenticated Spring headers |
| `/topic/presence/{userId}` | SUBSCRIBE allows self or active direct-chat peer; client publication is rejected |
| `/user/queue/errors` | User-targeted application errors |

The application prefix is `/app`, the local simple broker handles `/topic` and `/queue`, and `/user` resolves user destinations. CONNECT verifies identity, SUBSCRIBE checks protected conversation/presence resources, and application SEND handlers/services enforce membership and operation permissions. This is not a blanket authorization policy for arbitrary broker destinations.

## Message creation and concurrency

```text
REST POST / STOMP SEND
  -> authenticate -> validate payload
  -> lock conversation row (PESSIMISTIC_WRITE)
  -> validate active membership -> load sender/reply
  -> read latest sequence -> allocate next sequence
  -> insert message + attachments -> update conversation.lastMessage
  -> commit -> publish conversation event after commit
```

The conversation lock is acquired **before database-backed membership and sequence reads**. Under MySQL's default REPEATABLE READ, an ordinary membership SELECT before waiting for the lock could establish an older consistent snapshot. A later normal sequence lookup could then observe stale data even after the row lock was obtained. Lock-first ordering avoids that snapshot-ordering problem in the message-send transaction.

Sequences are local to each conversation. The row lock coordinates allocation; `UNIQUE(conversation_id, sequence)` is the integrity backstop, not the allocator. Sends to one conversation serialize; different conversations lock different rows. Higher latency in a contended conversation is the ordering/correctness trade-off.

### Read and event consistency

`ConversationMember.lastReadMessage` is a monotonic watermark: 50 -> 100 advances, 100 -> 50 and duplicate reads do not. One conditional JPQL database update compares the stored message sequence and changes the watermark atomically; concurrent older requests cannot overwrite a newer value. A read event is emitted only when the row advances.

Message/read events register an `afterCommit` transaction callback, so a rolled-back transaction does not broadcast committed-looking data. This prevents premature events, but it is **not** a transactional outbox: failure after commit can lose an event. Clients reconcile via REST. Typing remains transient.

## Message history

```http
GET /api/v1/conversations/{conversationId}/messages?beforeSequence=500&size=50
```

`beforeSequence` is a keyset cursor; history is descending by sequence, with sizes 1-100. Conceptually:

```sql
WHERE conversation_id = ? AND sequence < ?
ORDER BY sequence DESC
LIMIT ?
```

The `(conversation_id, sequence)` index supports seeking from the last loaded sequence instead of discarding deep OFFSET rows. Conversation lists separately use page/size offset pagination. History returns a `PageResponse` and loads attachments/reply data through a JPA entity graph; collection fetching with pagination can involve ORM count/in-memory work. The benchmark below measures the dedicated benchmark query paths, not every cost of that production hydration path.

## Presence architecture

Presence is ephemeral, **not** `users.is_online`. Redis server time scores each socket session's expiry; a Lua script atomically prunes, touches/removes sessions, updates the deadline index and publishes only effective transitions.

| Redis key/channel | Structure / purpose |
| --- | --- |
| `chat:presence:user:{userId}` | Sorted set: session ID -> expiry timestamp; cleanup TTL |
| `chat:presence:deadlines` | Sorted set: user -> earliest session expiry |
| `chat:presence:online` | Set used for transition detection |
| `chat:presence:events` | Pub/Sub channel carrying only `{userId, online}` |

```text
CONNECT -> register session
heartbeat -> refresh expiry
DISCONNECT -> remove that session
silent loss -> sweeper prunes expired sessions
remaining valid sessions > 0 -> Online; none -> Offline
```

Laptop + phone means Online; losing the laptop alone does not publish Offline. Duplicate disconnects are idempotent and an old session's disconnect does not remove a reconnected session. Heartbeats do not emit continuous status events: only OFFLINE -> ONLINE and ONLINE -> OFFLINE transitions publish.

| Timing | Default | Environment variable |
| --- | --- | --- |
| Heartbeat | 25 s | `PRESENCE_HEARTBEAT_MILLIS` |
| Session expiry | 75 s | `PRESENCE_EXPIRY_MILLIS` |
| Sweep | 5 s | `PRESENCE_SWEEP_MILLIS` |

The sweeper processes up to 200 due users per pass; large expiry backlogs require additional passes. Each instance forwards Redis transitions to its local STOMP broker. Authenticated `GET /api/v1/users/{userId}/presence` supplies a snapshot and `GET /api/v1/presence/config` supplies heartbeat timing. Both REST snapshots and topic subscriptions restrict visibility to self/active direct-chat peers; no session IDs, IPs or device metadata are exposed.

Redis Pub/Sub is non-durable. Periodic/reconnect snapshots reconcile missed events. The multi-key Lua script targets standalone Redis, not Redis Cluster. Shared presence state does not make the existing message broker horizontally distributed.

## Storage model

```text
User --< ConversationMember >-- Conversation --< Message --< MessageAttachment
 |               |                                |
 |               +-> lastReadMessage               +-> sender User
 |                                                +-> replyToMessage
 +--< RefreshToken                                (optional Message)

Conversation --< ConversationInviteLink
Conversation --< ConversationJoinRequest >-- User
PendingRegistration / PasswordResetToken -> temporary authentication records
```

Users own profile/auth-provider data; membership joins users to conversations with role/status and read state. Conversations retain a last-message reference. Messages retain sender, sequence, reply link and soft-delete/edit metadata; attachments cascade with the message. Invites and join requests reference their conversation, relevant users and review state. Pending registration/password-reset records persist the account-verification workflow.

Important constraints/indexes include unique user email/username/provider ID, unique direct-conversation key, unique `(conversation_id,user_id)` membership, unique `(conversation_id,sequence)` message ordering, conversation/sequence and sender indexes, user/status and conversation/status membership indexes, and unique refresh-token hash.

Redis is used only for OTP rate counters/cooldowns, single-use OAuth exchange codes and presence. It is not the message store, HTTP session store, general cache, durable notification system, distributed queue or distributed lock.

Flyway contains attachment foreign-key compatibility migrations, not a complete initial schema. Hibernate schema update remains enabled by default; do not describe Flyway alone as managing the whole schema lifecycle.

## Testing and local benchmarks

Run the backend suite:

```powershell
.\mvnw.cmd test
```

```sh
./mvnw test
```

The verified suite has **56 passing tests**, including service/unit, API/security, transaction/read-watermark integration, MySQL sequence-concurrency tests and real Redis presence tests. Docker is required for Testcontainers; their skip-on-unavailable setting means a skipped container test is not evidence of integration correctness.

The parent repository provides real-browser Playwright regression and k6 API read-path smoke. A recorded local run passed 7/7 journeys; a 10-VU smoke issued 1,795 requests with no HTTP failures and HTTP p95 282.95 ms. These are **local regression smoke results**, not production capacity.

### Local MySQL history benchmark

Retained [query summaries](performance/message-history/results/summary.csv) measure dedicated benchmark-profile endpoints with 10 VUs, page size 50, three repetitions, and datasets up to 500,000 messages. At 90% depth in the 500K dataset, keyset HTTP p95 was **17.23 ms**, versus OFFSET **4,380.11 ms**. This compares equivalent benchmark paths, not a production SLA or frontend/WebSocket latency.

### Local same-conversation send benchmark

Retained [send summaries](performance/concurrent-message-send/results/after/summary.csv) recorded **100 concurrent sends, 100 successful, 0 failures, 0 duplicate and 0 missing sequences**, across three repetitions. The 100-send p95 was 1,299.90 ms. One account and one conversation deliberately maximize row-lock contention; they do not model a full production workload.

Benchmark reproduction (recreates only the respective benchmark environment):

```powershell
.\performance\message-history\scripts\run-benchmark.ps1
.\performance\concurrent-message-send\scripts\run-benchmark.ps1
```

The history benchmark endpoints are enabled only by the `benchmark` Spring profile. Detailed harness instructions remain in the two public performance READMEs.

## Local development

Install Java 21 and Docker/Compose. Copy `.env.example` to `.env` and replace placeholders with local MySQL, SMTP, Google OAuth and Cloudinary configuration. Use distinct HS256 secrets of at least 32 random bytes. Never place Cloudinary secrets or signing keys in the client.

For a fully containerized backend, actual Compose services are `mysql`, `redis` and `app`:

```sh
docker compose up --build -d
docker compose logs -f app
docker compose down
```

The Compose database/cache use internal networking without host-published dependency ports. For a host JVM, provide MySQL/Redis reachable at the `.env` addresses instead, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

```sh
./mvnw spring-boot:run
```

Default backend port: 8080. OpenAPI UI: `/swagger-ui/index.html`; specification: `/v3/api-docs`; health: `/actuator/health`. Register Google's backend callback as `/login/oauth2/code/google`; `OAUTH2_REDIRECT_URI` is the separate frontend callback. CORS/socket origins must match the browser origin.
