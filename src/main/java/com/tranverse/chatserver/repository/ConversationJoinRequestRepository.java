package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.ConversationJoinRequest;
import com.tranverse.chatserver.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationJoinRequestRepository extends JpaRepository<ConversationJoinRequest, UUID> {
    boolean existsByConversationIdAndRequestedByUserIdAndStatus(
            UUID conversationId,
            UUID requestedByUserId,
            JoinRequestStatus status
    );

    Optional<ConversationJoinRequest> findByIdAndConversationId(UUID id, UUID conversationId);

    List<ConversationJoinRequest> findAllByConversationIdAndStatusOrderByCreatedAtAsc(
            UUID conversationId,
            JoinRequestStatus status
    );
}
