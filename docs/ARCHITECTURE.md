# Realtime Chat Application Architecture

## 1. System Overview

The backend is a modular monolith: authentication, users, conversations, messaging, media, and realtime delivery run in one Spring Boot process and share one MySQL database and transaction model.

```text
React SPA
   |
REST + STOMP/WebSocket
   |
Spring Boot modular monolith
   |-- MySQL
   |-- Redis
   |-- Cloudinary
   `-- Email / Google OAuth2
```

MySQL and Redis are infrastructure dependencies, not independently deployed business services.

## 2. Backend Modules

- **Authentication:** local credentials, OTP workflows, OAuth2 login, JWT issuance, refresh-token rotation, and logout.
- **Users:** profile retrieval/update and user search.
- **Conversations:** private/group creation, membership, roles, invitation links, join requests, and ownership transfer.
- **Messaging:** history, send, reply, edit, soft delete, and read-watermark updates.
- **Media:** authenticated image validation and Cloudinary upload.
- **Realtime/WebSocket:** STOMP authentication, subscription authorization, message events, typing, and read events.

REST and STOMP controllers translate transport requests into service calls. Services own use cases, transaction boundaries, and resource authorization. Repositories contain JPA persistence, queries, and locking. DTOs keep persistence entities out of public contracts.

## 3. Authentication

Local registration sends an email OTP before creating a user. Local login verifies the password, and password recovery uses a separate OTP and reset-token flow. Google OAuth2 creates or resolves the user through Spring Security, then redirects with a random exchange code rather than application tokens in the URL.

Access tokens authenticate REST and STOMP clients. Refresh tokens are rotated on use; only their hashes are stored. Reuse of a rotated token revokes active tokens in the same family. That security write runs in a separate `REQUIRES_NEW` transaction so it remains committed when the reuse request fails. Redis stores the OAuth2 exchange code for 60 seconds and atomically consumes it once.

## 4. Authorization

Spring Security permits only declared public authentication routes; other REST routes require an access JWT. Services enforce conversation membership and group `MEMBER`, `ADMIN`, and `OWNER` rules for the target resource. Message edit/delete operations validate both membership and sender or manager authority.

The STOMP `CONNECT` frame must provide `Authorization: Bearer <token>`. Subscriptions to `/topic/conversations/{id}` require active membership. Application destinations still execute service-level authorization, so knowledge of a conversation UUID is never sufficient by itself.

## 5. Realtime Messaging

Clients connect to `/ws` using native WebSocket or SockJS and communicate with STOMP:

- `/app/conversations/{id}/messages` sends messages.
- `/app/conversations/{id}/typing` publishes typing state.
- `/app/conversations/{id}/read` advances a read watermark.
- `/topic/conversations/{id}` delivers conversation events.
- `/user/queue/errors` delivers private protocol errors.

Events include message creation, update, deletion, typing, and read receipts. Message events are published after the database transaction commits, preventing clients from observing rolled-back state. Clients can recover after reconnect by fetching sequence-based history from the REST API.

The current simple STOMP broker is in-process and is not a distributed multi-instance broker.

## 6. Message Model

Each message belongs to one conversation and has a conversation-local monotonic `sequence`. A message can reference another message as a reply, be edited, or be soft-deleted. Attachments store image metadata and the Cloudinary URL. Each membership stores a last-read message as its read watermark.

## 7. Message Ordering and Concurrency

The final send path is:

```text
@Transactional
-> lock conversation with PESSIMISTIC_WRITE
-> validate active membership
-> load sender
-> read latest sequence
-> allocate max(sequence) + 1
-> persist message
-> update conversation.lastMessage
-> commit
-> publish realtime event after commit
```

Under MySQL `REPEATABLE READ`, a database read performed before acquiring the conversation lock can establish a consistent snapshot. A later sequence query could then observe stale state even after the lock is acquired. Locking the conversation before database-backed validation ensures subsequent sequence allocation observes the state committed by the previous lock owner.

`UNIQUE(conversation_id, sequence)` is the defensive database invariant. The conversation lock serializes sends only within the same conversation; different conversation rows can proceed independently.

## 8. Message History Pagination

The history endpoint accepts `beforeSequence` and a bounded page size (default 50). The repository selects rows with a lower sequence and orders them descending. The `(conversation_id, sequence)` index supports a range scan that starts near the requested cursor.

This keyset strategy fits append-heavy chat history because existing cursors remain stable as new messages arrive. Offset pagination supports arbitrary positional access but must traverse and discard increasingly many rows at deep positions and can shift under concurrent inserts.

## 9. Redis

Redis is used for:

- OTP send limits: 5 per email and 20 per IP in 15 minutes.
- OTP verification limits: 5 per email and 30 per IP in 5 minutes.
- A 60-second OTP resend cooldown.
- Single-use OAuth2 exchange codes with a 60-second TTL.

Redis is **not** currently used for message storage, general response caching, presence, HTTP sessions, the WebSocket broker, or distributed locks.

## 10. Database Model

Major entities are `User`, `Conversation`, `ConversationMember`, `Message`, `MessageAttachment`, `RefreshToken`, `PendingRegistration`, `PasswordResetToken`, `ConversationInviteLink`, and `ConversationJoinRequest`.

- Users and conversations have a many-to-many relationship represented by `ConversationMember`, which also stores role, status, join method, and read watermark.
- Conversations own messages; messages reference senders, optional reply targets, and attachments.
- A normalized unique direct key prevents duplicate private conversations.
- Membership is unique per `(conversation_id, user_id)` and can be reactivated after leaving.
- Message sequence is unique and indexed per conversation.
- Refresh tokens belong to users and token families; database state records rotation and revocation.
- Invite links and join requests belong to group conversations and carry their own state and expiry rules.

## 11. Transaction and Consistency Design

- A pessimistic conversation lock protects local message-sequence allocation.
- The unique conversation/sequence constraint rejects any duplicate that reaches persistence.
- Realtime events are registered for after-commit publication.
- Refresh-token reuse revokes the family in an independent transaction so failure handling cannot roll it back.
- A conditional database update advances the read watermark only when the requested message sequence is newer, preventing regression during concurrent updates.

## 12. Testing

JUnit and Mockito cover isolated service rules. Spring Boot, MockMvc, H2, and Redis test doubles cover API, security, JWT, OAuth2 exchange, transaction, and persistence integration. MySQL 8.4 Testcontainers executes concurrent message allocation with real database locking and isolation. The parent repository contains Playwright user-flow tests. k6 drives both pagination and same-conversation send benchmarks.

## 13. Infrastructure

The backend has a Docker image. The parent application repository packages the React frontend with Docker and Nginx and provides the system E2E Docker Compose environment. Backend-local Compose starts the application, MySQL, and Redis. Mailpit is used by the system test environment for email inspection. GitHub Actions run backend tests on pushes and pull requests.

## 14. External Integrations

- **Google OAuth2:** external identity authentication followed by the single-use code exchange.
- **Cloudinary:** authenticated image storage.
- **SMTP:** OTP and password-recovery email delivery.
- **Mailpit:** local/system-test SMTP capture rather than a production mail provider.

## 15. Architecture Limitations

- The system is a modular monolith, not a microservice architecture.
- The in-process STOMP broker supports a single backend instance and provides no distributed fan-out.
- Distributed presence is not implemented.
- The model tracks read watermarks, not a separate delivery-receipt state.
- Production distributed tracing is not configured.
- Benchmarks are local controlled measurements, not production capacity or an SLA.
- No claim is made for multi-instance realtime scalability.
