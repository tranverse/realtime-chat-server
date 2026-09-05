package com.tranverse.chatserver.dto.request.conversation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(
        @NotNull UUID userId
) {
}
