package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.enums.ConversationMemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @EntityGraph(attributePaths = {"lastMessage", "lastMessage.sender"})
    @Query(value = """
            select c from Conversation c
            join c.members m
            where m.user.id = :userId
              and m.status = :status
              and c.deletedAt is null
            order by coalesce(c.lastMessage.createdAt, c.createdAt) desc
            """,
            countQuery = """
            select count(c) from Conversation c
            join c.members m
            where m.user.id = :userId
              and m.status = :status
              and c.deletedAt is null
            """)
    Page<Conversation> findAllForUser(@Param("userId") UUID userId,
                                      @Param("status") ConversationMemberStatus status,
                                      Pageable pageable);

    @Query("""
            select distinct c from Conversation c
            where c.type = com.tranverse.chatserver.enums.ConversationType.PRIVATE
              and c.deletedAt is null
              and exists (select m1.id from ConversationMember m1
                          where m1.conversation = c and m1.user.id = :firstUserId
                            and m1.status = com.tranverse.chatserver.enums.ConversationMemberStatus.ACTIVE)
              and exists (select m2.id from ConversationMember m2
                          where m2.conversation = c and m2.user.id = :secondUserId
                            and m2.status = com.tranverse.chatserver.enums.ConversationMemberStatus.ACTIVE)
            """)
    Optional<Conversation> findActivePrivateConversation(@Param("firstUserId") UUID firstUserId,
                                                         @Param("secondUserId") UUID secondUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.deletedAt is null")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    Optional<Conversation> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Conversation> findByDirectKeyAndDeletedAtIsNull(String directKey);
}
