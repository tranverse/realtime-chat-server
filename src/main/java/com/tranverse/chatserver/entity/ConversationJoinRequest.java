package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.JoinRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationJoinRequest extends BaseEntity {

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status;

    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_member_id")
    private ConversationMember reviewedByMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_link_id")
    private ConversationInviteLink inviteLink;

    public static ConversationJoinRequest create(String message,
                                                 User requestedByUser,
                                                 Conversation conversation,
                                                 ConversationInviteLink inviteLink) {
        ConversationJoinRequest request = new ConversationJoinRequest();
        request.message = message;
        request.requestedByUser = requestedByUser;
        request.conversation = conversation;
        request.inviteLink = inviteLink;
        request.status = JoinRequestStatus.PENDING;
        return request;
    }

    public void approve(ConversationMember reviewer) {
        status = JoinRequestStatus.APPROVED;
        reviewedByMember = reviewer;
        reviewedAt = Instant.now();
    }

    public void reject(ConversationMember reviewer) {
        status = JoinRequestStatus.REJECTED;
        reviewedByMember = reviewer;
        reviewedAt = Instant.now();
    }

}
