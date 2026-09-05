package com.tranverse.chatserver.dto.response.message;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEventResponse(
        String type,
        UUID conversationId,
        UUID actorUserId,
        UUID messageId,
        Long sequence,
        ChatMessageResponse message
) {
    public static ChatEventResponse created(UUID actorUserId, ChatMessageResponse message) {
        return new ChatEventResponse(
                "MESSAGE_CREATED", message.conversationId(), actorUserId,
                message.id(), message.sequence(), message
        );
    }

    public static ChatEventResponse deleted(UUID conversationId, UUID actorUserId, UUID messageId, long sequence) {
        return new ChatEventResponse(
                "MESSAGE_DELETED", conversationId, actorUserId, messageId, sequence, null
        );
    }

    public static ChatEventResponse updated(UUID actorUserId, ChatMessageResponse message) {
        return new ChatEventResponse(
                "MESSAGE_UPDATED", message.conversationId(), actorUserId,
                message.id(), message.sequence(), message
        );
    }

    public static ChatEventResponse read(UUID conversationId, UUID actorUserId, UUID messageId, long sequence) {
        return new ChatEventResponse(
                "MESSAGES_READ", conversationId, actorUserId, messageId, sequence, null
        );
    }
}
