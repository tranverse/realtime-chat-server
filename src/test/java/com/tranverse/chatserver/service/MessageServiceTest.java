package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.message.CreateMessageRequest;
import com.tranverse.chatserver.dto.request.message.EditMessageRequest;
import com.tranverse.chatserver.dto.request.message.ReadConversationRequest;
import com.tranverse.chatserver.dto.response.message.ChatEventResponse;
import com.tranverse.chatserver.dto.response.message.ChatMessageResponse;
import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.*;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.ConversationRepository;
import com.tranverse.chatserver.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private UserService userService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendAssignsNextSequenceAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        User user = user(userId);
        Conversation conversation = Conversation.create(
                "Test", ConversationType.GROUP, 100, null);
        conversation.setId(conversationId);
        ConversationMember member = ConversationMember.create(
                conversation, user, ConversationMemberRole.MEMBER, JoinMethod.INVITATION, null);
        Message previous = Message.create(
                "Previous", MessageType.TEXT, 7L, conversation, user, null);

        when(conversationService.requireActiveMember(conversationId, userId)).thenReturn(member);
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        when(userService.getActiveUser(userId)).thenReturn(user);
        when(messageRepository.findTopByConversationIdOrderBySequenceDesc(conversationId))
                .thenReturn(Optional.of(previous));
        when(messageRepository.saveAndFlush(any(Message.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageResponse response = messageService.send(
                userId,
                conversationId,
                new CreateMessageRequest(" Hello ", MessageType.TEXT, null, List.of())
        );

        assertEquals(8L, response.sequence());
        assertEquals("Hello", response.content());
        assertNotNull(conversation.getLastMessage());
        assertEquals(8L, conversation.getLastMessage().getSequence());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId),
                any(ChatEventResponse.class)
        );
    }

    @Test
    void sendRejectsBlankTextMessageBeforeDatabaseAccess() {
        AppException exception = assertThrows(AppException.class, () -> messageService.send(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CreateMessageRequest("  ", MessageType.TEXT, null, List.of())
        ));

        assertEquals(ErrorCode.INVALID_MESSAGE, exception.getErrorCode());
        verifyNoInteractions(messageRepository, conversationRepository, conversationService, userService);
    }

    @Test
    void sendRejectsFileMessageWithoutAttachment() {
        AppException exception = assertThrows(AppException.class, () -> messageService.send(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CreateMessageRequest("file", MessageType.FILE, null, List.of())
        ));

        assertEquals(ErrorCode.INVALID_MESSAGE, exception.getErrorCode());
    }

    @Test
    void editUpdatesContentAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        User sender = user(userId);
        Conversation conversation = Conversation.create("Test", ConversationType.GROUP, 10, null);
        conversation.setId(conversationId);
        Message message = Message.create("Before", MessageType.TEXT, 3L, conversation, sender, null);
        message.setId(messageId);

        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);

        ChatMessageResponse response = messageService.edit(
                userId, messageId, new EditMessageRequest(" After "));

        assertEquals("After", response.content());
        assertNotNull(response.editedAt());
        verify(conversationService).requireActiveMember(conversationId, userId);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId), any(ChatEventResponse.class));
    }

    @Test
    void editRejectsUserWhoIsNotSender() {
        UUID senderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Conversation conversation = Conversation.create("Test", ConversationType.GROUP, 10, null);
        conversation.setId(conversationId);
        Message message = Message.create(
                "Before", MessageType.TEXT, 3L, conversation, user(senderId), null);
        message.setId(messageId);
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));

        AppException exception = assertThrows(AppException.class, () -> messageService.edit(
                actorId, messageId, new EditMessageRequest("After")));

        assertEquals(ErrorCode.FORBIDDEN_CONVERSATION, exception.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void markReadOnlyMovesReceiptForward() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        User user = user(userId);
        Conversation conversation = Conversation.create("Test", ConversationType.GROUP, 10, null);
        conversation.setId(conversationId);
        ConversationMember member = ConversationMember.create(
                conversation, user, ConversationMemberRole.MEMBER, JoinMethod.INVITATION, null);
        Message newer = Message.create("New", MessageType.TEXT, 9L, conversation, user, null);
        newer.setId(UUID.randomUUID());
        when(conversationService.requireActiveMember(conversationId, userId)).thenReturn(member);
        when(messageRepository.findByIdAndDeletedAtIsNull(newer.getId())).thenReturn(Optional.of(newer));

        messageService.markRead(userId, conversationId, new ReadConversationRequest(newer.getId()));

        assertSame(newer, member.getLastReadMessage());
        assertNotNull(member.getLastReadAt());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId), any(ChatEventResponse.class));
    }

    private User user(UUID id) {
        User user = User.create(
                "Test User", "test-user", "test@example.com", "hash", SystemRole.USER);
        user.setId(id);
        return user;
    }
}
