package com.tranverse.chatserver.dto.response.message;

public record MediaUploadResponse(
        String fileUrl,
        String fileType,
        long fileSize,
        String publicId,
        Integer width,
        Integer height
) {
}
