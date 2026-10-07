# Realtime Chat Server

Backend for realtime private and group chat.

**Stack:** Java 21, Spring Boot, Spring Security, MySQL, Redis, STOMP/WebSocket, Cloudinary and Docker.

[Frontend](https://github.com/tranverse/realtime-chat-client) · [Full system](https://github.com/tranverse/realtime-chat-application)

## Architecture

One Spring Boot application with a layered monolith architecture.

<img width="1352" height="987" alt="shape_LYU4OS4ipjuiwuCf546ll at 26-10-07 14 17 01" src="https://github.com/user-attachments/assets/e6c499b4-594e-4a3f-811d-aef6ffbc6399" />

- **MySQL:** users, conversations, messages, refresh-token hashes and image metadata.
- **Redis:** OTP rate limits, single-use OAuth2 codes and presence.
- **Cloudinary:** image storage.
- **Google OAuth2 / SMTP:** sign-in and verification/recovery emails.

Messages are saved before realtime events are published. Conversation row locks protect message sequence allocation; atomic updates prevent read receipts from moving backwards.

## Features

- OTP registration, password recovery, Google sign-in and JWT refresh-token rotation.
- Private/group chat, member roles, invitations and join requests.
- Messages, replies, edit/delete, image sharing, typing and read receipts.
- Multi-session Online/Offline presence and cursor-based message history.
- JWT authentication and conversation-level access checks for REST/STOMP.

## Run

Copy `.env.example` to `.env` and configure database, JWT, mail, Google OAuth2 and Cloudinary credentials.

```sh
docker compose up --build -d
docker compose logs -f app
docker compose down
```

Default port: **8080** · API docs: **/swagger-ui/index.html**.

## Tests

```powershell
.\mvnw.cmd test
```

Linux/macOS: `./mvnw test`. Docker is required for MySQL/Redis integration tests.

JUnit, Mockito and Testcontainers cover business rules, security, transactions, concurrency and presence. System Playwright/k6 tests live in the [parent repository](https://github.com/tranverse/realtime-chat-application).
