package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationMemberStatus;
import com.tranverse.chatserver.enums.ConversationType;
import com.tranverse.chatserver.enums.JoinMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMember extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    private Instant leftAt;

    private Instant removedAt;

    private Instant lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinMethod joinMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationMemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationMemberRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private Message lastReadMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_member_id")
    private ConversationMember invitedByMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_member_id")
    private ConversationMember approvedByMember;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = Instant.now();
    }

    public void leave(){
        this.status = ConversationMemberStatus.LEFT;
        this.leftAt = Instant.now();
    }

    public void remove(){
        this.status = ConversationMemberStatus.REMOVED;
        this.removedAt = Instant.now();
    }

    public void updateLastReadMessage(Message message){
        this.lastReadMessage = message;
        this.lastReadAt = Instant.now();
    }

    public void promoteToAdmin(){
        this.role = ConversationMemberRole.ADMIN;
    }

}
