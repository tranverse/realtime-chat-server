package com.tranverse.chatserver.dto.request.message;

import com.tranverse.chatserver.enums.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateMessageRequest(
        @Size(max = 5000) String content,
        @NotNull MessageType type,
        UUID replyToMessageId,
        @Valid @Size(max = 10) List<AttachmentRequest> attachments
) {
}
