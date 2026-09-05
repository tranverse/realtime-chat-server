package com.tranverse.chatserver.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageAttachment extends BaseEntity {

    @Column(nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    public static MessageAttachment create(String fileUrl, String fileType, Long fileSize, Message message) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.fileUrl = fileUrl;
        attachment.fileType = fileType;
        attachment.fileSize = fileSize;
        attachment.message = message;
        return attachment;
    }
}
