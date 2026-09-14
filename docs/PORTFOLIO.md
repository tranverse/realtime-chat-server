# Portfolio notes

## Mô tả ngắn cho CV

**Realtime Chat Backend — Java 21, Spring Boot 4, MySQL, Redis, WebSocket**

Thiết kế và triển khai modular monolith cho ứng dụng chat realtime: JWT/OAuth2,
refresh-token rotation và reuse detection, OTP rate limiting, chat riêng/nhóm,
role-based membership, link mời có phê duyệt, cursor pagination, read receipt và
STOMP/WebSocket authorization. Cung cấp cấu hình chạy local bằng Docker Compose và
kiểm thử tự động qua GitHub Actions.

## Điểm kỹ thuật nên trình bày khi phỏng vấn

- Chọn monolith vì phạm vi MVP cần transaction đơn giản và tốc độ phát triển; package
  boundary giúp giữ đường nâng cấp mà không nhận chi phí distributed system sớm.
- Message sequence được cấp trong transaction có pessimistic row lock và unique
  constraint, nên hai request đồng thời không nhận cùng sequence.
- Dùng sequence cursor thay offset để history không nhảy khi có message mới.
- Realtime event phát sau commit; tránh client nhận một message đã rollback.
- WebSocket được bảo vệ ở cả CONNECT, SUBSCRIBE và service write authorization.
- Refresh token chỉ lưu hash, rotate theo token family và phát hiện reuse.
- `direct_key` chuẩn hóa cặp UUID và unique để chat riêng có tính idempotent.
- DTO tách khỏi entity để tránh lazy-loading leak, vòng lặp JSON và coupling schema.

## Phạm vi đã hoàn thành

- Identity: register OTP, login, Google OAuth2, refresh, logout, logout-all, reset password.
- User: profile và search.
- Conversation: private/group, detail/list, settings và member limit.
- Membership: owner/admin/member, remove/leave, role và ownership transfer.
- Invitation: expiring link, direct join, approval queue và review.
- Messaging: text/image/file metadata, reply, edit, soft delete, cursor history.
- Realtime: message events, read receipt và typing indicator.
- Engineering: validation, centralized errors, database constraints/indexes, tests,
  actuator health, Docker Compose, CI và tài liệu.

## Giới hạn được chủ động giữ ngoài MVP

- Image upload: frontend gửi multipart có xác thực đến backend; backend kiểm tra file,
  upload lên Cloudinary và lưu metadata attachment cùng message.
- Simple broker: phù hợp một application instance. Multi-instance cần broker relay.
- Reaction, pin, full-text search, push notification và call là roadmap, không phải
  yêu cầu lõi để chứng minh backend fundamentals.
- Schema hiện dùng Hibernate update cùng các Flyway repair migration có phạm vi hẹp;
  chưa có baseline migration đầy đủ cho database mới.

Việc nêu rõ trade-off quan trọng hơn phóng đại mức độ hoàn thiện của dự án.
Repository thể hiện một MVP có thể chạy local và kiểm thử, đồng thời ghi lại giới hạn
thực tế. Public hosting nằm ngoài phạm vi đã được xác minh.
