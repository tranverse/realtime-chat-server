package com.tranverse.chatserver.dto.request.message;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReadConversationRequest(
        @NotNull UUID messageId
) {
}
