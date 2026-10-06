# User presence

Presence is ephemeral state owned by authenticated STOMP sessions. It is not a persisted user attribute. No schema migration is added.

## Redis state and transitions

- `chat:presence:user:{userId}`: sorted set of session IDs, scored by expiry in Redis server time.
- `chat:presence:deadlines`: sorted set of users, scored by their earliest session expiry.
- `chat:presence:online`: set used to detect effective online/offline transitions.
- `chat:presence:events`: Redis Pub/Sub channel carrying only `{userId, online}`.

One Lua script prunes expired sessions, updates/removes the supplied session, updates the expiry index, and publishes a transition only when effective state changes. Concurrent tabs/devices cannot overwrite each other's sessions. A delayed disconnect removes only its own session. Duplicate disconnects are idempotent. The session set has a TTL as a cleanup fallback; the expiry index enables offline events even when no clean disconnect arrives.

Each application instance listens to Redis transitions and forwards them to its local STOMP broker. The shared Redis state supports multiple application instances; this does not change the existing messaging broker or claim the application is deployed as a cluster. The Lua script uses multiple keys and targets the existing standalone Redis deployment, not Redis Cluster.

## Lifecycle and timing

`SessionConnectedEvent` registers the authenticated session. `/app/presence/heartbeat` refreshes it using the principal and session ID from Spring headers, never a client-supplied identity. `SessionDisconnectEvent` removes the session.

Defaults, configurable through `app.presence` or environment variables:

| Setting | Default | Environment variable |
| --- | --- | --- |
| Heartbeat | 25 seconds | `PRESENCE_HEARTBEAT_MILLIS` |
| Session expiry | 75 seconds | `PRESENCE_EXPIRY_MILLIS` |
| Expiry sweep | 5 seconds | `PRESENCE_SWEEP_MILLIS` |

The client obtains the heartbeat interval from authenticated `GET /api/v1/presence/config`. The sweeper processes up to 200 due users per pass. Offline detection normally takes expiry plus one sweep interval; large expiry backlogs can take additional passes. Sleeping/throttled browsers become offline when they stop refreshing their sessions and return online on a subsequent heartbeat/reconnect.

## API, events and privacy

- `GET /api/v1/users/{userId}/presence`: authenticated initial snapshot in the existing `ApiResponse` envelope.
- `/topic/presence/{userId}`: realtime online/offline transitions.
- Viewer must be the target user or share an active PRIVATE conversation with them. REST and STOMP subscriptions use the same access service. Group membership alone grants no presence access.
- Session IDs, IP addresses and Redis structures are not returned to clients.
- Client `SEND` frames to presence broker topics are rejected; only server transitions publish presence.
- OTP keys (`otp:*`) and OAuth exchange keys (`oauth2:exchange:*`) use separate namespaces and are unchanged.

Redis Pub/Sub is not a durable event log. Clients re-fetch on transport reconnect and periodically reconcile presence snapshots. Redis outages produce unavailable/unknown presence rather than fabricated offline status. Existing JWT CONNECT validation and conversation subscription checks remain in place.

## Verification

`RedisPresenceIntegrationTest` uses a real Redis 7.4 Testcontainer to verify first/final session transitions, multiple tabs/devices, heartbeat refresh, silent expiry, overlapping expiries, reconnect, concurrent session operations, transition deduplication and key isolation. `PresenceAccessTest` and WebSocket interceptor tests verify presence authorization. Testcontainers tests skip if Docker is unavailable; a skipped test is not proof of Redis correctness.

Full backend verification results are recorded in the parent implementation report, including whether Redis and MySQL Testcontainers ran or were skipped.
