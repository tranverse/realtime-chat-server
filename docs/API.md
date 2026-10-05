# API Contract

The REST base path is `/api/v1`. Protected endpoints require `Authorization: Bearer <access-token>`. OpenAPI documentation is available from `/swagger-ui.html` while the application is running.

## Authentication

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Send a registration OTP |
| POST | `/auth/register/verify` | Verify the OTP and create the account |
| POST | `/auth/register/resend` | Resend the registration OTP |
| POST | `/auth/login` | Issue access and refresh tokens |
| POST | `/auth/oauth2/exchange` | Consume a single-use OAuth2 exchange code |
| POST | `/auth/refresh` | Rotate a refresh token |
| POST | `/auth/logout` | Revoke the submitted refresh token |
| POST | `/auth/logout-all` | Revoke all active sessions for the authenticated user |
| POST | `/auth/forgot-password` | Send a password-reset OTP |
| POST | `/auth/forgot-password/verify` | Exchange the OTP for a reset token |
| POST | `/auth/reset-password` | Set a new password |
| GET | `/oauth2/authorization/google` | Start Google OAuth2 login |

## Users and Media

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/users/me` | Return the authenticated profile |
| PATCH | `/users/me` | Update profile fields |
| GET | `/users/search?q=&page=0&size=20` | Search users |
| POST | `/media/images` | Validate and upload an image to Cloudinary |

Image upload uses `multipart/form-data` with a `file` field. JPEG, PNG, WebP, and GIF are accepted up to the configured multipart limit (10 MB by default).

## Conversations

| Method | Path | Minimum authority |
| --- | --- | --- |
| GET | `/conversations?page=0&size=20` | Authenticated user |
| POST | `/conversations` | Authenticated user |
| GET | `/conversations/{id}` | Active member |
| PATCH | `/conversations/{id}` | Group admin |
| POST | `/conversations/{id}/members` | Group admin |
| DELETE | `/conversations/{id}/members/{userId}` | Group admin |
| PATCH | `/conversations/{id}/members/{userId}/role` | Group owner |
| POST | `/conversations/{id}/transfer-ownership` | Group owner |
| POST | `/conversations/{id}/leave` | Active member other than the owner |
| POST | `/conversations/{id}/invite-links` | Group admin |
| DELETE | `/conversations/{id}/invite-links/{linkId}` | Group admin |
| POST | `/conversations/invite-links/{code}/join` | Authenticated user |
| GET | `/conversations/{id}/join-requests` | Group admin |
| POST | `/conversations/{id}/join-requests/{requestId}/review` | Group admin |

`OWNER` has higher authority than `ADMIN`. An invitation with approval enabled creates a join request instead of adding the user immediately.

## Messages

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/conversations/{id}/messages?beforeSequence=&size=50` | Load newest-to-oldest keyset history |
| POST | `/conversations/{id}/messages` | Persist and publish a message |
| POST | `/conversations/{id}/read` | Advance the authenticated member's read watermark |
| PATCH | `/messages/{messageId}` | Edit a message owned by the sender |
| DELETE | `/messages/{messageId}` | Soft-delete as sender or authorized group manager |

Example send request:

```json
{
  "content": "Hello",
  "type": "TEXT",
  "replyToMessageId": null,
  "attachments": []
}
```

## STOMP/WebSocket

Connect to `/ws` using native WebSocket or SockJS and send `Authorization: Bearer <access-token>` in the STOMP `CONNECT` headers.

- Send: `/app/conversations/{conversationId}/messages`
- Read: `/app/conversations/{conversationId}/read`
- Typing: `/app/conversations/{conversationId}/typing`
- Conversation subscription: `/topic/conversations/{conversationId}`
- Private errors: `/user/queue/errors`

Published event types are `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED`, `MESSAGES_READ`, and `TYPING`. Subscription to a conversation topic requires active membership.
