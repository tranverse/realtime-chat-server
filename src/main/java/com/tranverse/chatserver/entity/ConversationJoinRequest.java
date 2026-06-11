package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.JoinRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
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


}
