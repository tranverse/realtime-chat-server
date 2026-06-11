package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.InviteLinkStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationInviteLink extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteLinkStatus status;

    @Column(nullable = false)
    private boolean requireApproval;

    @Column(nullable = false)
    private Instant expiredAt;

    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ConversationMember createdByMember;

    public void revoke() {
        this.status = InviteLinkStatus.REVOKED;
        this.revokedAt = Instant.now();
    }
}