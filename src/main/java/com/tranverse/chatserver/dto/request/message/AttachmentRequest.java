package com.tranverse.chatserver.dto.request.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AttachmentRequest(
        @NotBlank @Size(max = 1000) String fileUrl,
        @NotBlank @Size(max = 120) String fileType,
        @PositiveOrZero Long fileSize
) {
}
