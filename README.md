# Realtime Chat Application

A full-stack chat application for private and group conversations, realtime messaging, image sharing, and read tracking. This repository contains the Spring Boot modular-monolith backend.

## Features

- Email OTP registration, password login and recovery, Google OAuth2, JWT access tokens, and rotating refresh tokens.
- Private and group conversations with membership, roles, invitations, join approval, and ownership transfer.
- Text and image messages with replies, editing, soft deletion, sequence-based history, typing events, and read receipts.
- REST resource authorization plus authenticated and membership-authorized STOMP connections and subscriptions.

## Tech Stack

- Java 21, Spring Boot 4, Spring Security, Spring Data JPA
- MySQL 8.4, Redis 7.4
- STOMP over WebSocket/SockJS
- Cloudinary, SMTP, Google OAuth2
- JUnit, Mockito, MockMvc, Testcontainers, Docker Compose, k6

## Architecture

```text
React SPA
   |
REST + STOMP/WebSocket
   |
Spring Boot modular monolith
   |-- MySQL
   |-- Redis
   |-- Cloudinary
   `-- SMTP / Google OAuth2
```

The application is deployed as one backend process. The in-process STOMP broker is intended for a single application instance. See [Architecture](docs/ARCHITECTURE.md) and the [API contract](docs/API.md).

## Running Locally

Requirements: Java 21, MySQL 8, and Redis. Copy `.env.example` to `.env`, replace every placeholder, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Or start the backend and infrastructure with Docker Compose:

```powershell
docker compose up --build -d
```

The API is available at `http://localhost:8080`; Swagger UI is at `http://localhost:8080/swagger-ui.html`. See [Containerization](docs/CONTAINERIZATION.md) for configuration details.

## Testing

```powershell
.\mvnw.cmd test
```

The backend suite contains unit tests, Spring integration/API tests, security and transaction regression tests, and a MySQL Testcontainers concurrency test. System-level Playwright tests live in the parent repository.

## Performance Benchmarks

- [Message-history pagination](performance/message-history/README.md) compares sequence-based keyset pagination with offset pagination across 10K, 100K, and 500K messages.
- [Concurrent message creation](performance/concurrent-message-send/README.md) exercises 10, 25, 50, and 100 simultaneous sends to one conversation and verifies persisted sequence integrity.

Both suites are reproducible local Docker/k6 benchmarks. Their results are evidence for specific design decisions, not production capacity claims or service-level objectives.

## Repository Structure

```text
src/main/             Application code and configuration
src/test/             Unit and integration tests
docs/                 Architecture, API, and container documentation
performance/          Reproducible benchmark harnesses and result summaries
.github/workflows/    Backend CI workflows
compose.yml           Local backend, MySQL, and Redis environment
```

## Known Limitations

- Realtime delivery uses an in-process broker and is not designed for multi-instance fan-out.
- Redis does not store messages, presence, HTTP sessions, or WebSocket broker state.
- Delivery receipts, distributed presence, and production distributed tracing are not implemented.
- Local benchmark measurements are hardware- and environment-specific.
- The current schema setup uses Hibernate updates plus targeted Flyway repairs; a complete baseline migration is not yet provided.
