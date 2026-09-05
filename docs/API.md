# API contract

## Quy ước chung

Base URL: `/api/v1`. REST API dùng JSON và access token:

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

Response thành công:

```json
{
  "code": 200,
  "message": "Conversation retrieved successfully",
  "data": {}
}
```

Response lỗi:

```json
{
  "status": 403,
  "code": "CONVERSATION_FORBIDDEN",
  "message": "You do not have permission in this conversation",
  "timestamp": "2026-09-05T10:00:00"
}
```

Validation, UUID sai định dạng, authentication, authorization và database conflict
đều được ánh xạ thành HTTP status phù hợp; lỗi nội bộ không trả stack trace cho client.

## Authentication

| Method | Path | Auth | Chức năng |
| --- | --- | --- | --- |
| POST | `/auth/register` | Không | Gửi OTP đăng ký |
| POST | `/auth/register/verify` | Không | Xác thực OTP và tạo tài khoản |
| POST | `/auth/register/resend` | Không | Gửi lại OTP |
| POST | `/auth/login` | Không | Nhận access/refresh token |
| POST | `/auth/refresh` | Không | Rotate refresh token |
| POST | `/auth/logout` | Không | Thu hồi refresh token được gửi lên |
| POST | `/auth/logout-all` | Có | Thu hồi mọi phiên của current user |
| POST | `/auth/forgot-password` | Không | Gửi reset OTP |
| POST | `/auth/forgot-password/verify` | Không | Đổi OTP thành reset token ngắn hạn |
| POST | `/auth/reset-password` | Không | Đặt mật khẩu mới |
| GET | `/oauth2/authorization/google` | Không | Bắt đầu Google OAuth2 |

## User

| Method | Path | Chức năng |
| --- | --- | --- |
| GET | `/users/me` | Lấy hồ sơ current user |
| PATCH | `/users/me` | Cập nhật name, username, avatar, phone, dob |
| GET | `/users/search?q=&page=0&size=20` | Tìm theo name, username hoặc email |

## Conversation và membership

Tạo chat riêng:

```json
{
  "type": "PRIVATE",
  "memberIds": ["other-user-uuid"]
}
```

Tạo group:

```json
{
  "type": "GROUP",
  "name": "Backend Team",
  "memberIds": ["user-uuid-1", "user-uuid-2"],
  "maxMembers": 100,
  "avatar": null
}
```

| Method | Path | Quyền tối thiểu |
| --- | --- | --- |
| GET | `/conversations?page=0&size=20` | MEMBER |
| POST | `/conversations` | User đã đăng nhập |
| GET | `/conversations/{id}` | MEMBER |
| PATCH | `/conversations/{id}` | ADMIN |
| POST | `/conversations/{id}/members` | ADMIN |
| DELETE | `/conversations/{id}/members/{userId}` | ADMIN |
| PATCH | `/conversations/{id}/members/{userId}/role` | OWNER |
| POST | `/conversations/{id}/transfer-ownership` | OWNER |
| POST | `/conversations/{id}/leave` | MEMBER, trừ OWNER |
| POST | `/conversations/{id}/invite-links` | ADMIN |
| DELETE | `/conversations/{id}/invite-links/{linkId}` | ADMIN |
| POST | `/conversations/invite-links/{code}/join` | User đã đăng nhập |
| GET | `/conversations/{id}/join-requests` | ADMIN |
| POST | `/conversations/{id}/join-requests/{requestId}/review` | ADMIN |

`OWNER` cao hơn `ADMIN`; ADMIN không thể xóa ADMIN khác hoặc đổi OWNER. Link mời có
`requireApproval=true` trả join request thay vì thêm thành viên ngay.

## Message

| Method | Path | Chức năng |
| --- | --- | --- |
| GET | `/conversations/{id}/messages?beforeSequence=&size=50` | Lấy lịch sử mới đến cũ |
| POST | `/conversations/{id}/messages` | Gửi và phát event realtime |
| POST | `/conversations/{id}/read` | Đánh dấu đã đọc đến message |
| PATCH | `/messages/{messageId}` | Sender chỉnh sửa nội dung |
| DELETE | `/messages/{messageId}` | Sender hoặc group manager xóa mềm |

Payload message:

```json
{
  "content": "Thiết kế này ổn nhé",
  "type": "TEXT",
  "replyToMessageId": null,
  "attachments": []
}
```

`IMAGE` và `FILE` phải có ít nhất một attachment:

```json
{
  "content": "ERD",
  "type": "IMAGE",
  "attachments": [
    {
      "fileUrl": "https://object-storage.example/erd.png",
      "fileType": "image/png",
      "fileSize": 245100
    }
  ]
}
```

Cursor `beforeSequence` tránh lỗi trùng/thiếu thường gặp khi dùng offset trong lúc
conversation liên tục nhận message mới.

## STOMP/WebSocket

Handshake tại `/ws`; hỗ trợ native WebSocket và SockJS.

1. CONNECT với native header `Authorization: Bearer <access-token>`.
2. SUBSCRIBE `/topic/conversations/{conversationId}` để nhận event.
3. SUBSCRIBE `/user/queue/errors` để nhận lỗi riêng.
4. SEND message đến `/app/conversations/{conversationId}/messages`.
5. SEND read receipt đến `/app/conversations/{conversationId}/read`.
6. SEND `{ "typing": true }` đến `/app/conversations/{conversationId}/typing`.

Event envelope:

```json
{
  "type": "MESSAGE_CREATED",
  "conversationId": "uuid",
  "actorUserId": "uuid",
  "messageId": "uuid",
  "sequence": 42,
  "message": {}
}
```

Event types: `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED`, `MESSAGES_READ`
và `TYPING`.
