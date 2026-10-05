# Local Containerization

This document describes local packaging and execution. The topology remains a modular monolith.

```text
Client -> chat-server:8080 -> MySQL:3306
                         `-> Redis:6379
```

## Docker Compose

Create `.env` from `.env.example`, replace every placeholder, then run:

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
```

Stop containers while retaining named MySQL and Redis volumes:

```bash
docker compose down
```

`docker compose down --volumes` intentionally removes local data.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `REDIS_HOST`, `REDIS_PORT` | Redis connection |
| `JWT_ACCESS_KEY`, `JWT_REFRESH_KEY` | Separate token-signing secrets |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials |
| `GG_CLIENT_ID`, `GG_CLIENT_SECRET` | Google OAuth2 client |
| `OAUTH2_REDIRECT_URI` | Frontend OAuth2 callback |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Image storage |
| `JPA_DDL_AUTO` | Local Hibernate schema mode; defaults to `update` |
| `APP_PORT` | Published application port; defaults to `8080` |

Do not commit `.env`; `.env.example` contains placeholders only.

## CI and Scaling Boundary

The workflows under `.github/workflows/` run Maven verification and backend tests. MySQL-specific concurrency behavior is covered with a Testcontainers test when Docker is available.

Running multiple backend instances would require a shared STOMP broker relay and distributed realtime state. Adding those dependencies would not require splitting the business application into microservices, but the current Compose topology does not implement that deployment model.
