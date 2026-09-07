package com.tranverse.chatserver.dto.request.conversation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateInviteLinkRequest(
        boolean requireApproval,
        @Min(1) @Max(720) Integer expiresInHours
) {
}
