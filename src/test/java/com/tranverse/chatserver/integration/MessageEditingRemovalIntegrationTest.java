package com.tranverse.chatserver.integration;

import com.tranverse.chatserver.controller.WebSocketMessageController;
import com.tranverse.chatserver.dto.request.conversation.CreateConversationRequest;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.*;
import com.tranverse.chatserver.repository.*;
import com.tranverse.chatserver.service.ConversationService;
import com.tranverse.chatserver.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:editing_removed;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MessageEditingRemovalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ConversationService conversations;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository repository;
    @Autowired MessageService messages;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void oldRestEditIsUnsupportedAndDoesNotModifyHistoricalData() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User owner = users.save(User.create("Owner", suffix, suffix + "@example.com", "hash", SystemRole.USER));
        UUID group = conversations.create(owner.getId(), new CreateConversationRequest(
                ConversationType.GROUP, "Test", Set.of(), 10, null)).id();
        Instant historicalEdit = Instant.parse("2026-06-01T00:00:00Z");
        UUID id = new TransactionTemplate(transactions).execute(status -> {
            Message saved = Message.create("Preserved historical content", MessageType.TEXT, 1,
                    conversationRepository.findById(group).orElseThrow(), owner, null);
            saved.setEditedAt(historicalEdit);
            return repository.save(saved).getId();
        });
        mvc.perform(patch("/api/v1/messages/" + id)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"tampered\"}"))
                .andExpect(status().isMethodNotAllowed());
        Message persisted = repository.findById(id).orElseThrow();
        assertEquals("Preserved historical content", persisted.getContent());
        assertEquals(historicalEdit, persisted.getEditedAt());
        var history = messages.getHistory(owner.getId(), group, null, 50).items().getFirst();
        assertEquals("Preserved historical content", history.content());
        assertEquals(historicalEdit, history.editedAt());
    }

    @Test
    void stompExposesOnlySendReadAndTypingWithoutAnEditHandler() {
        Set<String> destinations = new HashSet<>();
        Arrays.stream(WebSocketMessageController.class.getDeclaredMethods()).forEach(method -> {
            MessageMapping mapping = method.getAnnotation(MessageMapping.class);
            if (mapping != null) destinations.addAll(Arrays.asList(mapping.value()));
        });
        assertEquals(Set.of("/conversations/{conversationId}/messages",
                "/conversations/{conversationId}/read", "/conversations/{conversationId}/typing"), destinations);
    }
}
