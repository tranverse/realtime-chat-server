package com.tranverse.chatserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.UUID;

/** Metadata invalidations use a user queue so newly added members need not know a topic yet. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public record ConversationChanged(String type, UUID conversationId, String conversationName,
                                      String reason, boolean added, boolean removed) { }

    public void publish(UUID conversationId, String name, String reason, Set<UUID> recipients,
                        Set<UUID> added, Set<UUID> removed) {
        Set<UUID> targets = Set.copyOf(recipients);
        Set<UUID> addedTargets = Set.copyOf(added);
        Set<UUID> removedTargets = Set.copyOf(removed);
        Runnable delivery = () -> targets.forEach(userId -> {
            try {
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/conversations",
                        new ConversationChanged("CONVERSATION_CHANGED", conversationId, name, reason,
                                addedTargets.contains(userId), removedTargets.contains(userId)));
            } catch (RuntimeException exception) {
                // A failed best-effort event must not turn an already committed mutation into an HTTP error.
                log.warn("Conversation {} update delivery failed for {}", conversationId, userId, exception);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delivery.run(); }
            });
        }
    }
}
