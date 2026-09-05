package com.tranverse.chatserver.dto.request.conversation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @Size(min = 1, max = 50) String name,
        @Min(2) @Max(500) Integer maxMembers,
        @Size(max = 500) String avatar
) {
}
