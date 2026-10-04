# Realtime Chat Server

Backend modular monolith for a realtime chat application, built with Java 21, Spring Boot 4,
MySQL, Redis, JWT, Google OAuth2, Cloudinary, and STOMP/WebSocket.

This is a **modular monolith**, not a microservice system. A single Spring Boot application
contains all business modules and runs as one backend process.

Detailed documentation:

- [Architecture](docs/ARCHITECTURE.md)
- [API contract](docs/API.md)
- [Local containerization reference](docs/CONTAINERIZATION.md)
- [Portfolio / interview notes](docs/PORTFOLIO.md)

## MVP features

- Email OTP registration, login, refresh-token rotation, logout, and password recovery.
- Google OAuth2 login with a short-lived, single-use code exchange.
- User profile management and user search.
- Private and group conversations with member, admin, and owner roles.
- Expiring invitation links with optional administrator approval.
- Text and image messages, replies, editing, soft deletion, cursor history, and read receipts.
- Realtime typing and message events over STOMP/WebSocket.
- JWT authentication on CONNECT and membership authorization on SUBSCRIBE.

## Local development

Requirements: Java 21, MySQL 8, and Redis.

1. Copy `.env.example` to `.env` and provide the required configuration.
2. Start MySQL and Redis.
3. Run:

```powershell
.\mvnw.cmd spring-boot:run
```

The server starts at `http://localhost:8080` by default. Swagger UI is available at
`http://localhost:8080/swagger-ui.html`.

Alternatively, run the monolith, MySQL, and Redis locally with Docker Compose:

```powershell
docker compose up --build -d
```

## Main REST API

All endpoints below, except public authentication endpoints, require the header
`Authorization: Bearer <access-token>`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | Sign in |
| POST | `/api/v1/auth/register` | Send a registration OTP |
| POST | `/api/v1/auth/register/verify` | Verify the OTP and create an account |
| POST | `/api/v1/auth/oauth2/exchange` | Exchange a one-time OAuth2 code |
| POST | `/api/v1/auth/refresh` | Rotate the refresh token |
| POST | `/api/v1/auth/logout-all` | Revoke every active session |
| GET | `/api/v1/users/me` | Get the current profile |
| PATCH | `/api/v1/users/me` | Update the current profile |
| GET | `/api/v1/users/search?q=` | Search for users |
| GET/POST | `/api/v1/conversations` | List or create conversations |
| GET/PATCH | `/api/v1/conversations/{id}` | Read or update a conversation |
| POST | `/api/v1/conversations/{id}/members` | Add members |
| DELETE | `/api/v1/conversations/{id}/members/{userId}` | Remove a member |
| PATCH | `/api/v1/conversations/{id}/members/{userId}/role` | Change a member role |
| POST | `/api/v1/conversations/{id}/transfer-ownership` | Transfer group ownership |
| POST | `/api/v1/conversations/{id}/invite-links` | Create an invitation link |
| POST | `/api/v1/conversations/invite-links/{code}/join` | Join through an invitation |
| GET | `/api/v1/conversations/{id}/messages` | Load cursor-based message history |
| POST | `/api/v1/conversations/{id}/messages` | Send a message |
| POST | `/api/v1/conversations/{id}/read` | Update the read receipt |
| PATCH | `/api/v1/messages/{messageId}` | Edit a message |
| DELETE | `/api/v1/messages/{messageId}` | Soft-delete a message |

## WebSocket/STOMP

- Handshake endpoint: `/ws` (native WebSocket or SockJS).
- CONNECT native header: `Authorization: Bearer <access-token>`.
- Send messages: `/app/conversations/{conversationId}/messages`.
- Mark messages as read: `/app/conversations/{conversationId}/read`.
- Publish typing state: `/app/conversations/{conversationId}/typing`.
- Subscribe to conversation events: `/topic/conversations/{conversationId}`.
- Subscribe to personal errors: `/user/queue/errors`.

Message payload:

```json
{
  "content": "Hello",
  "type": "TEXT",
  "replyToMessageId": null,
  "attachments": []
}
```

The server publishes `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED`,
`MESSAGES_READ`, and `TYPING` events.

## Testing

```powershell
.\mvnw.cmd test
```

The suite combines isolated JUnit/Mockito tests with Spring Boot + MockMvc integration
tests that exercise HTTP endpoints, security, JWT authentication, services, and H2-backed
persistence together. A MySQL 8.4 Testcontainers regression test verifies concurrent
same-conversation message sequence allocation with 10 and 25 threads.

## Message-history pagination benchmark

A controlled local MySQL 8.4/k6 benchmark compared sequence-based keyset pagination with
offset pagination using 10 concurrent users and datasets of 10K, 100K, and 500K messages.
At 500K messages and 90% page depth, keyset pagination measured approximately **17 ms p95**
versus **4.38 s p95** for offset pagination. These local measurements validate the design
choice; they are not production capacity or an SLA.

See [FINAL_BACKEND_PORTFOLIO_REPORT.md](FINAL_BACKEND_PORTFOLIO_REPORT.md) for the verified
result summary and portfolio-safe wording.

## Concurrent message benchmark

The local MySQL 8.4/k6 benchmark sent 10, 25, 50, and 100 messages concurrently to the
same conversation. At the maximum tested level, all **100/100 sends succeeded** with zero
failed requests, duplicate sequences, or missing sequences across three repetitions.
Same-conversation writes intentionally serialize on a per-conversation pessimistic lock;
different conversations lock different rows and can proceed independently.

The benchmark also exposed and verified the correction of a transaction-ordering bug:

```text
Before: membership SELECT → snapshot → conversation lock → stale max(sequence)
        → duplicate allocation → unique constraint rejection

After:  conversation lock → membership check → sequence read → persist message
```

See [CONCURRENT_SEQUENCE_FIX_REPORT.md](CONCURRENT_SEQUENCE_FIX_REPORT.md) for the
before/after results, MySQL regression coverage, and limitations. These results describe
100 concurrent same-conversation sends in a local benchmark, not global or distributed
production capacity.

The current MVP uses Hibernate schema updates together with targeted Flyway repair
migrations. A complete baseline migration is not yet included, so do not switch a fresh
database to `JPA_DDL_AUTO=validate` without adding and verifying that baseline first.
