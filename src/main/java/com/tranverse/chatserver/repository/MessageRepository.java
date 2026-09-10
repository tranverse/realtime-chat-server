package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = {
            "sender",
            "attachments",
            "replyToMessage",
            "replyToMessage.sender"
    })
    @Query("""
            select distinct m from Message m
            where m.conversation.id = :conversationId
              and (:beforeSequence is null or m.sequence < :beforeSequence)
            order by m.sequence desc
            """)
    Page<Message> findHistory(@Param("conversationId") UUID conversationId,
                              @Param("beforeSequence") Long beforeSequence,
                              Pageable pageable);

    Optional<Message> findTopByConversationIdOrderBySequenceDesc(UUID conversationId);

    Optional<Message> findTopByConversationIdAndDeletedAtIsNullOrderBySequenceDesc(UUID conversationId);

    long countByConversationIdAndSequenceGreaterThanAndSenderIdNotAndDeletedAtIsNull(
            UUID conversationId,
            long sequence,
            UUID senderId
    );

    @EntityGraph(attributePaths = {"sender", "replyToMessage", "replyToMessage.sender", "attachments"})
    Optional<Message> findByIdAndDeletedAtIsNull(UUID id);
}
