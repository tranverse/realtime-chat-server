package com.tranverse.chatserver.dto.response.message;

import com.tranverse.chatserver.entity.MessageAttachment;

import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String fileUrl,
        String fileType,
        Long fileSize
) {
    public static AttachmentResponse from(MessageAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(), attachment.getFileUrl(),
                attachment.getFileType(), attachment.getFileSize()
        );
    }
}
