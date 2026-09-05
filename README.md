# Realtime Chat Server

Backend monolith cho ứng dụng chat realtime, xây dựng bằng Java 21, Spring Boot 4,
MySQL, Redis, JWT, Google OAuth2 và STOMP/WebSocket.

Đây là **modular monolith**, không phải microservice. Một Spring Boot application chứa
toàn bộ business module và được deploy như một đơn vị.

Tài liệu chi tiết:

- [Architecture](docs/ARCHITECTURE.md)
- [API contract](docs/API.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Portfolio / interview notes](docs/PORTFOLIO.md)

## Chức năng MVP

- Đăng ký bằng OTP email, đăng nhập, refresh-token rotation, logout và đặt lại mật khẩu.
- Đăng nhập Google OAuth2.
- Xem/cập nhật hồ sơ và tìm kiếm người dùng.
- Tạo chat riêng hoặc nhóm; quản lý thành viên, admin và chủ nhóm.
- Link mời có thời hạn, tùy chọn yêu cầu quản trị viên phê duyệt.
- Gửi/chỉnh sửa tin nhắn text/image/file, reply, xóa mềm, lịch sử dạng cursor và read receipt.
- Typing indicator realtime.
- Realtime event qua STOMP/WebSocket; xác thực JWT khi CONNECT và kiểm tra thành viên khi SUBSCRIBE.

## Chạy local

Yêu cầu: Java 21, MySQL 8 và Redis.

1. Sao chép `.env.example` thành `.env` rồi điền cấu hình thật.
2. Khởi động MySQL và Redis.
3. Chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Server mặc định chạy tại `http://localhost:8080`. Swagger UI ở
`http://localhost:8080/swagger-ui.html`.

Hoặc chạy toàn bộ monolith + MySQL + Redis:

```powershell
docker compose up --build -d
```

## REST API chính

Tất cả API bên dưới, trừ `/api/v1/auth/**`, yêu cầu header
`Authorization: Bearer <access-token>`.

| Method | Endpoint | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | Đăng nhập |
| POST | `/api/v1/auth/register` | Gửi OTP đăng ký |
| POST | `/api/v1/auth/register/verify` | Xác thực OTP và tạo tài khoản |
| POST | `/api/v1/auth/refresh` | Rotate refresh token |
| POST | `/api/v1/auth/logout-all` | Thu hồi phiên trên mọi thiết bị |
| GET | `/api/v1/users/me` | Hồ sơ hiện tại |
| PATCH | `/api/v1/users/me` | Cập nhật hồ sơ |
| GET | `/api/v1/users/search?q=` | Tìm người dùng |
| GET/POST | `/api/v1/conversations` | Danh sách/tạo hội thoại |
| GET/PATCH | `/api/v1/conversations/{id}` | Chi tiết/cập nhật nhóm |
| POST | `/api/v1/conversations/{id}/members` | Thêm thành viên |
| DELETE | `/api/v1/conversations/{id}/members/{userId}` | Xóa thành viên |
| PATCH | `/api/v1/conversations/{id}/members/{userId}/role` | Đổi MEMBER/ADMIN |
| POST | `/api/v1/conversations/{id}/transfer-ownership` | Chuyển chủ nhóm |
| POST | `/api/v1/conversations/{id}/invite-links` | Tạo link mời |
| POST | `/api/v1/conversations/invite-links/{code}/join` | Tham gia bằng link |
| GET | `/api/v1/conversations/{id}/messages` | Lịch sử tin nhắn |
| POST | `/api/v1/conversations/{id}/messages` | Gửi tin nhắn |
| POST | `/api/v1/conversations/{id}/read` | Cập nhật read receipt |
| PATCH | `/api/v1/messages/{messageId}` | Chỉnh sửa tin nhắn |
| DELETE | `/api/v1/messages/{messageId}` | Xóa mềm tin nhắn |

## WebSocket/STOMP

- Handshake endpoint: `/ws` (native WebSocket hoặc SockJS).
- CONNECT native header: `Authorization: Bearer <access-token>`.
- Client gửi tin: `/app/conversations/{conversationId}/messages`.
- Client cập nhật đã đọc: `/app/conversations/{conversationId}/read`.
- Client gửi typing indicator: `/app/conversations/{conversationId}/typing`.
- Subscribe event: `/topic/conversations/{conversationId}`.
- Subscribe lỗi cá nhân: `/user/queue/errors`.

Payload gửi tin:

```json
{
  "content": "Xin chào",
  "type": "TEXT",
  "replyToMessageId": null,
  "attachments": []
}
```

Server phát các event `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED`,
`MESSAGES_READ` và `TYPING`.

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Trong môi trường production, đặt `JPA_DDL_AUTO=validate` và quản lý thay đổi schema
bằng migration trước khi triển khai.
