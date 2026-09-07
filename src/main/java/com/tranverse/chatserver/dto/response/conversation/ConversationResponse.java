package com.tranverse.chatserver.dto.response.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tranverse.chatserver.dto.response.message.ChatMessageResponse;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationResponse(
        UUID id,
        String name,
        ConversationType type,
        String avatar,
        Integer maxMembers,
        long memberCount,
        long unreadCount,
        ConversationMemberRole myRole,
        ChatMessageResponse lastMessage,
        List<ConversationMemberResponse> members,
        Instant createdAt,
        Instant updatedAt
) {
}
