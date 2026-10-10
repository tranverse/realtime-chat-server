package com.tranverse.chatserver.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ConversationEventPublisherTest {
    final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    final ConversationEventPublisher publisher = new ConversationEventPublisher(template);
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void publishesOnlyAfterCommitAndIncludesNewAndRemovedRecipients() {
        UUID group = UUID.randomUUID(), owner = UUID.randomUUID(), added = UUID.randomUUID(), removed = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher.publish(group, "Team", "MEMBERS_ADDED", Set.of(owner, added, removed), Set.of(added), Set.of(removed));
        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(template).convertAndSendToUser(added.toString(), "/queue/conversations", new ConversationEventPublisher.ConversationChanged("CONVERSATION_CHANGED", group, "Team", "MEMBERS_ADDED", true, false));
        verify(template).convertAndSendToUser(removed.toString(), "/queue/conversations", new ConversationEventPublisher.ConversationChanged("CONVERSATION_CHANGED", group, "Team", "MEMBERS_ADDED", false, true));
    }
    @Test void rollbackDoesNotDeliver() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher.publish(UUID.randomUUID(), "Team", "ROLE_UPDATED", Set.of(UUID.randomUUID()), Set.of(), Set.of());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(1));
        verifyNoInteractions(template);
    }
    @Test void noTransactionDoesNotPublishUncommittedMutation() {
        publisher.publish(UUID.randomUUID(), "Team", "ROLE_UPDATED", Set.of(UUID.randomUUID()), Set.of(), Set.of());
        verifyNoInteractions(template);
    }
}
