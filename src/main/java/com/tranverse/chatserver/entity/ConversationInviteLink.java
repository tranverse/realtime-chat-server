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

    public boolean isUsable() {
        return status == InviteLinkStatus.ACTIVE && expiredAt.isAfter(Instant.now());
    }

    public static ConversationInviteLink create(String code,
                                                boolean requireApproval,
                                                Instant expiredAt,
                                                Conversation conversation,
                                                ConversationMember createdByMember) {
        ConversationInviteLink link = new ConversationInviteLink();
        link.code = code;
        link.requireApproval = requireApproval;
        link.expiredAt = expiredAt;
        link.conversation = conversation;
        link.createdByMember = createdByMember;
        link.status = InviteLinkStatus.ACTIVE;
        return link;
    }
}
