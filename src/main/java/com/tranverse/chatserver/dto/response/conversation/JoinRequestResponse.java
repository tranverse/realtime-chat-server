package com.tranverse.chatserver.dto.response.conversation;

import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.ConversationJoinRequest;
import com.tranverse.chatserver.enums.JoinRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestResponse(
        UUID id,
        UUID conversationId,
        UserSummaryResponse requestedBy,
        String message,
        JoinRequestStatus status,
        Instant createdAt,
        Instant reviewedAt
) {
    public static JoinRequestResponse from(ConversationJoinRequest request) {
        return new JoinRequestResponse(
                request.getId(), request.getConversation().getId(),
                UserSummaryResponse.from(request.getRequestedByUser()),
                request.getMessage(), request.getStatus(), request.getCreatedAt(), request.getReviewedAt()
        );
    }
}
