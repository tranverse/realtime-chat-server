package com.tranverse.chatserver.dto.request.conversation;

import com.tranverse.chatserver.enums.ConversationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateConversationRequest(
        @NotNull ConversationType type,
        @Size(max = 50) String name,
        @Size(max = 100) Set<UUID> memberIds,
        @Min(2) @Max(500) Integer maxMembers,
        @Size(max = 500) String avatar
) {
}
