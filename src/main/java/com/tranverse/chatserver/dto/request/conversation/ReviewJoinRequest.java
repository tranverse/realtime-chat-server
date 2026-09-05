package com.tranverse.chatserver.dto.request.conversation;

import jakarta.validation.constraints.NotNull;

public record ReviewJoinRequest(
        @NotNull Boolean approved
) {
}
