package com.tranverse.chatserver.dto.response.message;

import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.enums.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        String content,
        MessageType type,
        long sequence,
        UserSummaryResponse sender,
        ReplyMessageResponse replyTo,
        List<AttachmentResponse> attachments,
        Instant editedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatMessageResponse from(Message message) {
        boolean deleted = message.isDeleted();
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                deleted ? null : message.getContent(),
                message.getType(),
                message.getSequence(),
                UserSummaryResponse.from(message.getSender()),
                ReplyMessageResponse.from(message.getReplyToMessage()),
                deleted
                        ? List.of()
                        : message.getAttachments().stream().map(AttachmentResponse::from).toList(),
                message.getEditedAt(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
