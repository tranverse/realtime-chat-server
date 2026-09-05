package com.tranverse.chatserver.dto.response.message;

import java.time.Instant;
import java.util.UUID;

public record TypingEventResponse(
        String type,
        UUID conversationId,
        UUID actorUserId,
        boolean typing,
        Instant timestamp
) {
    public static TypingEventResponse of(UUID conversationId, UUID actorUserId, boolean typing) {
        return new TypingEventResponse(
                "TYPING", conversationId, actorUserId, typing, Instant.now());
    }
}
