package com.tranverse.chatserver.dto.response.conversation;

import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationMemberStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationMemberResponse(
        UUID id,
        UserSummaryResponse user,
        ConversationMemberRole role,
        ConversationMemberStatus status,
        Instant joinedAt,
        Long lastReadSequence
) {
    public static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getId(),
                UserSummaryResponse.from(member.getUser()),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getLastReadMessage() == null ? null : member.getLastReadMessage().getSequence()
        );
    }
}
