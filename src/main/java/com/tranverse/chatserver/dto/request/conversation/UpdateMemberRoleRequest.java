package com.tranverse.chatserver.dto.request.conversation;

import com.tranverse.chatserver.enums.ConversationMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull ConversationMemberRole role
) {
}
