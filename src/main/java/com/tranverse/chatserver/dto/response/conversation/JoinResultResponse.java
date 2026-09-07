package com.tranverse.chatserver.dto.response.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinResultResponse(
        boolean joined,
        ConversationResponse conversation,
        JoinRequestResponse joinRequest
) {
    public static JoinResultResponse joined(ConversationResponse conversation) {
        return new JoinResultResponse(true, conversation, null);
    }

    public static JoinResultResponse pending(JoinRequestResponse request) {
        return new JoinResultResponse(false, null, request);
    }
}
