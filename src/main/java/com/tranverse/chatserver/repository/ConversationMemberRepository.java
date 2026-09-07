package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.enums.ConversationMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    Optional<ConversationMember> findByConversationIdAndUserIdAndStatus(
            UUID conversationId,
            UUID userId,
            ConversationMemberStatus status
    );

    List<ConversationMember> findAllByConversationIdAndStatusOrderByJoinedAtAsc(
            UUID conversationId,
            ConversationMemberStatus status
    );

    long countByConversationIdAndStatus(UUID conversationId, ConversationMemberStatus status);

    boolean existsByConversationIdAndUserIdAndStatus(
            UUID conversationId,
            UUID userId,
            ConversationMemberStatus status
    );
}
