package com.tranverse.chatserver.dto.request.conversation;

import jakarta.validation.constraints.Size;

public record JoinConversationRequest(
        @Size(max = 500) String message
) {
}
