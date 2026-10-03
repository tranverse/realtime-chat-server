package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.enums.ConversationMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConversationMember member
               set member.lastReadMessage = :message,
                   member.lastReadAt = :readAt
             where member.id = :memberId
               and (member.lastReadMessage is null
                    or exists (
                        select currentRead.id
                          from Message currentRead
                         where currentRead = member.lastReadMessage
                           and currentRead.sequence < :sequence
                    ))
            """)
    int advanceLastReadIfNewer(@Param("memberId") UUID memberId,
                               @Param("message") Message message,
                               @Param("sequence") long sequence,
                               @Param("readAt") Instant readAt);
}
