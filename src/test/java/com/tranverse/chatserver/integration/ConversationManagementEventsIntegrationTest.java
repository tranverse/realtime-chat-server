package com.tranverse.chatserver.integration;

import com.tranverse.chatserver.dto.request.conversation.*;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.*;
import com.tranverse.chatserver.repository.UserRepository;
import com.tranverse.chatserver.service.ConversationService;
import com.tranverse.chatserver.service.ConversationEventPublisher.ConversationChanged;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:group_events;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
class ConversationManagementEventsIntegrationTest {
    @Autowired ConversationService service;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean SimpMessagingTemplate messaging;

    User user(String name) {
        String suffix = UUID.randomUUID().toString();
        return users.save(User.create(name, suffix, suffix + "@example.com", "hash", SystemRole.USER));
    }

    @Test void addRoleTransferAndRemovalNotifyAffectedUserAfterPersisting() {
        User owner = user("Owner"), member = user("Member");
        UUID group = service.create(owner.getId(), new CreateConversationRequest(ConversationType.GROUP, "Team", Set.of(), 10, null)).id();
        clearInvocations(messaging);
        doAnswer(invocation -> {
            ConversationChanged event = invocation.getArgument(2);
            if (event.added()) assertEquals(ConversationMemberRole.MEMBER, service.getConversation(member.getId(), group).myRole());
            return null;
        }).when(messaging).convertAndSendToUser(eq(member.getId().toString()), eq("/queue/conversations"), any(Object.class));
        service.addMembers(owner.getId(), group, new AddMembersRequest(Set.of(member.getId())));
        assertTrue(service.getConversations(member.getId(), 0, 30).items().stream().anyMatch(item -> item.id().equals(group)),
                "A newly joined group must be listed even before its first message");
        verify(messaging).convertAndSendToUser(eq(member.getId().toString()), eq("/queue/conversations"), argThat((Object value) -> value instanceof ConversationChanged e && e.added() && e.reason().equals("MEMBERS_ADDED")));
        service.updateMemberRole(owner.getId(), group, member.getId(), new UpdateMemberRoleRequest(ConversationMemberRole.ADMIN));
        assertEquals(ConversationMemberRole.ADMIN, service.getConversation(member.getId(), group).myRole());
        service.transferOwnership(owner.getId(), group, new TransferOwnershipRequest(member.getId()));
        assertEquals(ConversationMemberRole.OWNER, service.getConversation(member.getId(), group).myRole());
        assertEquals(ConversationMemberRole.ADMIN, service.getConversation(owner.getId(), group).myRole());
        service.removeMember(member.getId(), group, owner.getId());
        verify(messaging).convertAndSendToUser(eq(owner.getId().toString()), eq("/queue/conversations"), argThat((Object value) -> value instanceof ConversationChanged e && e.removed()));
        assertThrows(RuntimeException.class, () -> service.getConversation(owner.getId(), group));
    }

    @Test void rolledBackMembershipDoesNotEmitEventOrBecomeVisible() {
        User owner = user("Owner"), member = user("Member");
        UUID group = service.create(owner.getId(), new CreateConversationRequest(ConversationType.GROUP, "Team", Set.of(), 10, null)).id();
        clearInvocations(messaging);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.addMembers(owner.getId(), group, new AddMembersRequest(Set.of(member.getId())));
            verifyNoInteractions(messaging);
            status.setRollbackOnly();
        });
        verifyNoInteractions(messaging);
        assertThrows(RuntimeException.class, () -> service.getConversation(member.getId(), group));
    }
}
