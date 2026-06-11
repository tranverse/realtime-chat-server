package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    @Column(nullable = false, length = 5000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(nullable = false)
    private Long sequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "sender_user_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private Message replyToMessage;

}
