package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.ConversationType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_direct_key",
                columnNames = "direct_key"
        ),
        indexes = {
                @Index(name = "idx_conversation_type", columnList = "type"),
                @Index(name = "idx_conversation_updated_at", columnList = "updatedAt")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    private Integer maxMembers;

    private String avatar;

    @Column(name = "direct_key", length = 73)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private Message lastMessage;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConversationMember> members = new ArrayList<>();

    public static Conversation create(String name, ConversationType type, Integer maxMembers, String avatar) {
        Conversation conversation = new Conversation();
        conversation.name = name;
        conversation.type = type;
        conversation.maxMembers = type == ConversationType.PRIVATE ? 2 : maxMembers;
        conversation.avatar = avatar;
        return conversation;
    }

}
