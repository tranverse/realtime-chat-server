package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.message.CreateMessageRequest;
import com.tranverse.chatserver.dto.request.message.EditMessageRequest;
import com.tranverse.chatserver.dto.request.message.ReadConversationRequest;
import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.auth.MessageResponse;
import com.tranverse.chatserver.dto.response.message.ChatMessageResponse;
import com.tranverse.chatserver.service.MessageService;
import com.tranverse.chatserver.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.<PageResponse<ChatMessageResponse>>builder()
                .code(200)
                .message("Messages retrieved successfully")
                .data(messageService.getHistory(
                        SecurityUtils.userId(jwt), conversationId, beforeSequence, size))
                .build());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody CreateMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ChatMessageResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Message sent successfully")
                        .data(messageService.send(
                                SecurityUtils.userId(jwt), conversationId, request))
                        .build());
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<MessageResponse>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ReadConversationRequest request) {
        messageService.markRead(SecurityUtils.userId(jwt), conversationId, request);
        return ResponseEntity.ok(ApiResponse.<MessageResponse>builder()
                .code(200)
                .message("Conversation marked as read")
                .data(new MessageResponse("Read receipt updated"))
                .build());
    }

    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> editMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.<ChatMessageResponse>builder()
                .code(200)
                .message("Message updated successfully")
                .data(messageService.edit(SecurityUtils.userId(jwt), messageId, request))
                .build());
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId) {
        messageService.delete(SecurityUtils.userId(jwt), messageId);
        return ResponseEntity.noContent().build();
    }
}
