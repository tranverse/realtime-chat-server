package com.tranverse.chatserver.dto.response.message;

import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.Message;

import java.util.UUID;

public record ReplyMessageResponse(
        UUID id,
        String content,
        long sequence,
        UserSummaryResponse sender
) {
    public static ReplyMessageResponse from(Message message) {
        if (message == null) {
            return null;
        }
        return new ReplyMessageResponse(
                message.getId(), message.isDeleted() ? null : message.getContent(),
                message.getSequence(), UserSummaryResponse.from(message.getSender())
        );
    }
}
