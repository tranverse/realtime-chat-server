package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.conversation.*;
import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.auth.MessageResponse;
import com.tranverse.chatserver.dto.response.conversation.*;
import com.tranverse.chatserver.service.ConversationService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Validated
public class ConversationController {
    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponse.<PageResponse<ConversationResponse>>builder()
                .code(200)
                .message("Conversations retrieved successfully")
                .data(conversationService.getConversations(SecurityUtils.userId(jwt), page, size))
                .build());
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.<ConversationResponse>builder()
                .code(200)
                .message("Conversation retrieved successfully")
                .data(conversationService.getConversation(SecurityUtils.userId(jwt), conversationId))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse conversation = conversationService.create(SecurityUtils.userId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ConversationResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Conversation created successfully")
                        .data(conversation)
                        .build());
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.<ConversationResponse>builder()
                .code(200)
                .message("Conversation updated successfully")
                .data(conversationService.update(SecurityUtils.userId(jwt), conversationId, request))
                .build());
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<ApiResponse<ConversationResponse>> addMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AddMembersRequest request) {
        return ResponseEntity.ok(ApiResponse.<ConversationResponse>builder()
                .code(200)
                .message("Members added successfully")
                .data(conversationService.addMembers(SecurityUtils.userId(jwt), conversationId, request))
                .build());
    }

    @DeleteMapping("/{conversationId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<MessageResponse>> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId) {
        conversationService.removeMember(SecurityUtils.userId(jwt), conversationId, targetUserId);
        return ResponseEntity.ok(ApiResponse.<MessageResponse>builder()
                .code(200)
                .message("Member removed successfully")
                .data(new MessageResponse("Member removed"))
                .build());
    }

    @PatchMapping("/{conversationId}/members/{targetUserId}/role")
    public ResponseEntity<ApiResponse<ConversationMemberResponse>> updateMemberRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.<ConversationMemberResponse>builder()
                .code(200)
                .message("Member role updated successfully")
                .data(conversationService.updateMemberRole(
                        SecurityUtils.userId(jwt), conversationId, targetUserId, request))
                .build());
    }

    @PostMapping("/{conversationId}/transfer-ownership")
    public ResponseEntity<ApiResponse<ConversationResponse>> transferOwnership(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        return ResponseEntity.ok(ApiResponse.<ConversationResponse>builder()
                .code(200)
                .message("Ownership transferred successfully")
                .data(conversationService.transferOwnership(
                        SecurityUtils.userId(jwt), conversationId, request))
                .build());
    }

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<ApiResponse<MessageResponse>> leaveConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId) {
        conversationService.leave(SecurityUtils.userId(jwt), conversationId);
        return ResponseEntity.ok(ApiResponse.<MessageResponse>builder()
                .code(200)
                .message("Conversation left successfully")
                .data(new MessageResponse("Conversation left"))
                .build());
    }

    @PostMapping("/{conversationId}/invite-links")
    public ResponseEntity<ApiResponse<InviteLinkResponse>> createInviteLink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody CreateInviteLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<InviteLinkResponse>builder()
                        .code(HttpStatus.CREATED.value())
                        .message("Invite link created successfully")
                        .data(conversationService.createInviteLink(
                                SecurityUtils.userId(jwt), conversationId, request))
                        .build());
    }

    @DeleteMapping("/{conversationId}/invite-links/{linkId}")
    public ResponseEntity<Void> revokeInviteLink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @PathVariable UUID linkId) {
        conversationService.revokeInviteLink(SecurityUtils.userId(jwt), conversationId, linkId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite-links/{code}/join")
    public ResponseEntity<ApiResponse<JoinResultResponse>> joinByInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String code,
            @Valid @RequestBody JoinConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.<JoinResultResponse>builder()
                .code(200)
                .message("Invite processed successfully")
                .data(conversationService.joinByInvite(SecurityUtils.userId(jwt), code, request))
                .build());
    }

    @GetMapping("/{conversationId}/join-requests")
    public ResponseEntity<ApiResponse<List<JoinRequestResponse>>> getJoinRequests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.<List<JoinRequestResponse>>builder()
                .code(200)
                .message("Join requests retrieved successfully")
                .data(conversationService.getPendingJoinRequests(
                        SecurityUtils.userId(jwt), conversationId))
                .build());
    }

    @PostMapping("/{conversationId}/join-requests/{requestId}/review")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> reviewJoinRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewJoinRequest request) {
        return ResponseEntity.ok(ApiResponse.<JoinRequestResponse>builder()
                .code(200)
                .message("Join request reviewed successfully")
                .data(conversationService.reviewJoinRequest(
                        SecurityUtils.userId(jwt), conversationId, requestId, request))
                .build());
    }
}
