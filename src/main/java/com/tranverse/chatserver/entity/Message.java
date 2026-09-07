package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_conversation_sequence",
                columnNames = {"conversation_id", "sequence"}
        ),
        indexes = {
                @Index(name = "idx_message_conversation_sequence", columnList = "conversation_id,sequence"),
                @Index(name = "idx_message_sender", columnList = "sender_user_id")
        })
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

    private Instant editedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageAttachment> attachments = new ArrayList<>();

    public static Message create(String content,
                                 MessageType type,
                                 long sequence,
                                 Conversation conversation,
                                 User sender,
                                 Message replyToMessage) {
        Message message = new Message();
        message.content = content;
        message.type = type;
        message.sequence = sequence;
        message.conversation = conversation;
        message.sender = sender;
        message.replyToMessage = replyToMessage;
        return message;
    }

    public void addAttachment(MessageAttachment attachment) {
        attachments.add(attachment);
    }

    public void edit(String content) {
        this.content = content;
        this.editedAt = Instant.now();
    }
}
