package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.ConversationInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationInviteLinkRepository extends JpaRepository<ConversationInviteLink, UUID> {
    Optional<ConversationInviteLink> findByCode(String code);
    Optional<ConversationInviteLink> findByIdAndConversationId(UUID id, UUID conversationId);
}
