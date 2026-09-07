package com.tranverse.chatserver.dto.response.conversation;

import com.tranverse.chatserver.entity.ConversationInviteLink;
import com.tranverse.chatserver.enums.InviteLinkStatus;

import java.time.Instant;
import java.util.UUID;

public record InviteLinkResponse(
        UUID id,
        UUID conversationId,
        String code,
        boolean requireApproval,
        InviteLinkStatus status,
        Instant expiredAt
) {
    public static InviteLinkResponse from(ConversationInviteLink link) {
        return new InviteLinkResponse(
                link.getId(), link.getConversation().getId(), link.getCode(),
                link.isRequireApproval(), link.getStatus(), link.getExpiredAt()
        );
    }
}
