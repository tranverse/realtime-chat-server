package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.message.CreateMessageRequest;
import com.tranverse.chatserver.dto.request.message.ReadConversationRequest;
import com.tranverse.chatserver.dto.request.message.TypingRequest;
import com.tranverse.chatserver.dto.response.message.TypingEventResponse;
import com.tranverse.chatserver.dto.response.message.WebSocketErrorResponse;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.service.MessageService;
import com.tranverse.chatserver.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/conversations/{conversationId}/messages")
    public void sendMessage(Principal principal,
                            @DestinationVariable UUID conversationId,
                            @Valid @Payload CreateMessageRequest request) {
        messageService.send(currentUserId(principal), conversationId, request);
    }

    @MessageMapping("/conversations/{conversationId}/read")
    public void markRead(Principal principal,
                         @DestinationVariable UUID conversationId,
                         @Valid @Payload ReadConversationRequest request) {
        messageService.markRead(currentUserId(principal), conversationId, request);
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(Principal principal,
                       @DestinationVariable UUID conversationId,
                       @Payload TypingRequest request) {
        UUID userId = currentUserId(principal);
        conversationService.requireActiveMember(conversationId, userId);
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId,
                TypingEventResponse.of(conversationId, userId, request.typing())
        );
    }

    @MessageExceptionHandler(AppException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleAppException(AppException exception) {
        return new WebSocketErrorResponse(
                exception.getErrorCode().getCode(), exception.getMessage(), Instant.now());
    }

    private UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
