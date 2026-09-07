package com.tranverse.chatserver.dto.response.message;

import java.time.Instant;

public record WebSocketErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
