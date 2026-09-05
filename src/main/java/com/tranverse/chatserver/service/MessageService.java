package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.message.AttachmentRequest;
import com.tranverse.chatserver.dto.request.message.CreateMessageRequest;
import com.tranverse.chatserver.dto.request.message.EditMessageRequest;
import com.tranverse.chatserver.dto.request.message.ReadConversationRequest;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.message.ChatEventResponse;
import com.tranverse.chatserver.dto.response.message.ChatMessageResponse;
import com.tranverse.chatserver.entity.*;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.MessageType;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.ConversationRepository;
import com.tranverse.chatserver.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public PageResponse<ChatMessageResponse> getHistory(UUID userId,
                                                        UUID conversationId,
                                                        Long beforeSequence,
                                                        int size) {
        conversationService.requireActiveMember(conversationId, userId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Message> messages = messageRepository.findHistory(
                conversationId,
                beforeSequence,
                PageRequest.of(0, safeSize)
        );
        return PageResponse.from(messages, ChatMessageResponse::from);
    }

    @Transactional
    public ChatMessageResponse send(UUID userId,
                                    UUID conversationId,
                                    CreateMessageRequest request) {
        validateMessage(request);
        conversationService.requireActiveMember(conversationId, userId);
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        User sender = userService.getActiveUser(userId);

        Message replyTo = null;
        if (request.replyToMessageId() != null) {
            replyTo = messageRepository.findByIdAndDeletedAtIsNull(request.replyToMessageId())
                    .filter(message -> message.getConversation().getId().equals(conversationId))
                    .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND,
                            "Reply message was not found in this conversation"));
        }

        long sequence = messageRepository.findTopByConversationIdOrderBySequenceDesc(conversationId)
                .map(message -> message.getSequence() + 1)
                .orElse(1L);
        Message message = Message.create(
                normalizeContent(request.content()),
                request.type(),
                sequence,
                conversation,
                sender,
                replyTo
        );
        for (AttachmentRequest attachmentRequest : safeAttachments(request.attachments())) {
            message.addAttachment(MessageAttachment.create(
                    attachmentRequest.fileUrl().trim(),
                    attachmentRequest.fileType().trim(),
                    attachmentRequest.fileSize(),
                    message
            ));
        }

        Message saved = messageRepository.saveAndFlush(message);
        conversation.setLastMessage(saved);
        conversationRepository.save(conversation);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        publishAfterCommit(ChatEventResponse.created(userId, response));
        return response;
    }

    @Transactional
    public ChatMessageResponse edit(UUID userId, UUID messageId, EditMessageRequest request) {
        Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        conversationService.requireActiveMember(message.getConversation().getId(), userId);
        if (!message.getSender().getId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION,
                    "Only the sender can edit this message");
        }
        if (message.getType() == MessageType.SYSTEM) {
            throw new AppException(ErrorCode.INVALID_MESSAGE,
                    "System messages cannot be edited");
        }
        message.edit(request.content().trim());
        Message saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        publishAfterCommit(ChatEventResponse.updated(userId, response));
        return response;
    }

    @Transactional
    public void markRead(UUID userId,
                         UUID conversationId,
                         ReadConversationRequest request) {
        ConversationMember member = conversationService.requireActiveMember(conversationId, userId);
        Message message = messageRepository.findByIdAndDeletedAtIsNull(request.messageId())
                .filter(candidate -> candidate.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (member.getLastReadMessage() == null
                || message.getSequence() > member.getLastReadMessage().getSequence()) {
            member.updateLastReadMessage(message);
            ChatEventResponse event = ChatEventResponse.read(
                    conversationId, userId, message.getId(), message.getSequence());
            publishAfterCommit(event);
        }
    }

    @Transactional
    public void delete(UUID userId, UUID messageId) {
        Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        UUID conversationId = message.getConversation().getId();
        ConversationMember member = conversationService.requireActiveMember(conversationId, userId);
        boolean isSender = message.getSender().getId().equals(userId);
        boolean isManager = member.getRole() == ConversationMemberRole.OWNER
                || member.getRole() == ConversationMemberRole.ADMIN;
        if (!isSender && !isManager) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION,
                    "You cannot delete this message");
        }

        message.softDelete();
        messageRepository.save(message);
        Conversation conversation = message.getConversation();
        if (conversation.getLastMessage() != null
                && conversation.getLastMessage().getId().equals(messageId)) {
            conversation.setLastMessage(
                    messageRepository
                            .findTopByConversationIdAndDeletedAtIsNullOrderBySequenceDesc(conversationId)
                            .orElse(null)
            );
            conversationRepository.save(conversation);
        }
        publishAfterCommit(ChatEventResponse.deleted(
                conversationId, userId, messageId, message.getSequence()));
    }

    private void validateMessage(CreateMessageRequest request) {
        List<AttachmentRequest> attachments = safeAttachments(request.attachments());
        boolean hasContent = request.content() != null && !request.content().isBlank();
        if (request.type() == MessageType.SYSTEM) {
            throw new AppException(ErrorCode.INVALID_MESSAGE,
                    "Clients cannot create system messages");
        }
        if (request.type() == MessageType.TEXT && !hasContent) {
            throw new AppException(ErrorCode.INVALID_MESSAGE);
        }
        if ((request.type() == MessageType.IMAGE || request.type() == MessageType.FILE)
                && attachments.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_MESSAGE,
                    "An image or file message requires at least one attachment");
        }
        if (!hasContent && attachments.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_MESSAGE);
        }
    }

    private List<AttachmentRequest> safeAttachments(List<AttachmentRequest> attachments) {
        return attachments == null ? List.of() : attachments;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private void publishAfterCommit(ChatEventResponse event) {
        Runnable publish = () -> messagingTemplate.convertAndSend(
                "/topic/conversations/" + event.conversationId(), event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
