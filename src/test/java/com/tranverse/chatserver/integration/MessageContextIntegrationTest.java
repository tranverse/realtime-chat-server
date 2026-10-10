package com.tranverse.chatserver.integration;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:message_context;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MessageContextIntegrationTest {
    @Autowired MessageService messages;
    @Autowired ConversationService conversations;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository repository;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;

    record Fixture(User owner, UUID conversationId, List<UUID> ids) { }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        User owner = users.save(User.create("Context Owner", suffix, suffix + "@example.com", "hash", SystemRole.USER));
        UUID id = conversations.create(owner.getId(), new CreateConversationRequest(
                ConversationType.GROUP, "History", Set.of(), 10, null)).id();
        List<UUID> ids = new TransactionTemplate(transactions).execute(status -> {
            var conversation = conversationRepository.findById(id).orElseThrow();
            List<UUID> result = new ArrayList<>();
            for (int sequence = 1; sequence <= 250; sequence++) {
                Message message = repository.save(Message.create("message-" + sequence,
                        MessageType.TEXT, sequence, conversation, owner, null));
                result.add(message.getId());
            }
            return result;
        });
        return new Fixture(owner, id, ids);
    }

    @Test
    void deepWindowIsBoundedChronologicalAndIncludesTarget() {
        Fixture f = fixture();
        var window = messages.getContext(f.owner().getId(), f.conversationId(), f.ids().get(119));
        assertEquals(41, window.items().size());
        assertEquals(100, window.items().getFirst().sequence());
        assertEquals(140, window.items().getLast().sequence());
        assertEquals(f.ids().get(119), window.items().get(20).id());
        assertTrue(window.hasOlder());
        assertTrue(window.hasNewer());
    }

    @Test
    void beginningAndEndHaveCorrectCursorBoundaries() {
        Fixture f = fixture();
        var first = messages.getContext(f.owner().getId(), f.conversationId(), f.ids().getFirst());
        assertEquals(21, first.items().size());
        assertFalse(first.hasOlder());
        assertTrue(first.hasNewer());
        var last = messages.getContext(f.owner().getId(), f.conversationId(), f.ids().getLast());
        assertFalse(last.hasNewer());
        assertTrue(last.hasOlder());
        assertEquals(250, last.items().getLast().sequence());
    }

    @Test
    void deletedTargetIsAReadableTombstoneWithoutContentOrAttachments() {
        Fixture f = fixture();
        messages.delete(f.owner().getId(), f.ids().get(119));
        var target = messages.getContext(f.owner().getId(), f.conversationId(), f.ids().get(119)).items().get(20);
        assertNull(target.content());
        assertTrue(target.attachments().isEmpty());
    }

    @Test
    void apiEnforcesAuthenticationMembershipAndConversationScope() throws Exception {
        Fixture f = fixture();
        String path = "/api/v1/conversations/" + f.conversationId() + "/messages/" + f.ids().get(119) + "/context";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(f.owner().getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(41));
        UUID other = conversations.create(f.owner().getId(), new CreateConversationRequest(
                ConversationType.GROUP, "Other", Set.of(), 10, null)).id();
        mvc.perform(get("/api/v1/conversations/" + other + "/messages/" + f.ids().get(119) + "/context")
                        .with(jwt().jwt(token -> token.subject(f.owner().getId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conversations/" + f.conversationId() + "/messages/" + UUID.randomUUID() + "/context")
                        .with(jwt().jwt(token -> token.subject(f.owner().getId().toString()))))
                .andExpect(status().isNotFound());
    }
}
