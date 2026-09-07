package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.conversation.CreateConversationRequest;
import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.*;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private ConversationInviteLinkRepository inviteLinkRepository;
    @Mock
    private ConversationJoinRequestRepository joinRequestRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void privateConversationRequiresExactlyOneOtherUser() {
        UUID userId = UUID.randomUUID();
        when(userService.getActiveUser(userId)).thenReturn(user(userId));

        AppException exception = assertThrows(AppException.class, () -> conversationService.create(
                userId,
                new CreateConversationRequest(
                        ConversationType.PRIVATE, null, Set.of(), null, null)
        ));

        assertEquals(ErrorCode.INVALID_CONVERSATION, exception.getErrorCode());
    }

    @Test
    void groupConversationRequiresAName() {
        UUID userId = UUID.randomUUID();
        when(userService.getActiveUser(userId)).thenReturn(user(userId));

        AppException exception = assertThrows(AppException.class, () -> conversationService.create(
                userId,
                new CreateConversationRequest(
                        ConversationType.GROUP, "  ", Set.of(), 10, null)
        ));

        assertEquals(ErrorCode.INVALID_CONVERSATION, exception.getErrorCode());
    }

    @Test
    void groupOwnerMustTransferOwnershipBeforeLeaving() {
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = group(conversationId);
        ConversationMember owner = ConversationMember.create(
                conversation, user(ownerId), ConversationMemberRole.OWNER, JoinMethod.CREATED, null);
        when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
                .thenReturn(Optional.of(conversation));
        when(memberRepository.findByConversationIdAndUserIdAndStatus(
                conversationId, ownerId, ConversationMemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        AppException exception = assertThrows(AppException.class,
                () -> conversationService.leave(ownerId, conversationId));

        assertEquals(ErrorCode.OWNER_CANNOT_LEAVE, exception.getErrorCode());
    }

    @Test
    void adminCannotRemoveOwner() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = group(conversationId);
        ConversationMember admin = ConversationMember.create(
                conversation, user(adminId), ConversationMemberRole.ADMIN, JoinMethod.INVITATION, null);
        ConversationMember owner = ConversationMember.create(
                conversation, user(ownerId), ConversationMemberRole.OWNER, JoinMethod.CREATED, null);
        when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
                .thenReturn(Optional.of(conversation));
        when(memberRepository.findByConversationIdAndUserIdAndStatus(
                conversationId, adminId, ConversationMemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(memberRepository.findByConversationIdAndUserIdAndStatus(
                conversationId, ownerId, ConversationMemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        AppException exception = assertThrows(AppException.class,
                () -> conversationService.removeMember(adminId, conversationId, ownerId));

        assertEquals(ErrorCode.FORBIDDEN_CONVERSATION, exception.getErrorCode());
    }

    private Conversation group(UUID id) {
        Conversation conversation = Conversation.create("Test group", ConversationType.GROUP, 10, null);
        conversation.setId(id);
        return conversation;
    }

    private User user(UUID id) {
        User user = User.create(
                "Test User", "user-" + id, id + "@example.com", "hash", SystemRole.USER);
        user.setId(id);
        return user;
    }
}
