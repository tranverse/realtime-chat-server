package com.tranverse.chatserver.integration;

import com.tranverse.chatserver.dto.request.message.CreateMessageRequest;
import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationType;
import com.tranverse.chatserver.enums.JoinMethod;
import com.tranverse.chatserver.enums.MessageType;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import com.tranverse.chatserver.repository.ConversationRepository;
import com.tranverse.chatserver.repository.MessageRepository;
import com.tranverse.chatserver.repository.UserRepository;
import com.tranverse.chatserver.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MessageSequenceMySqlConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("chat_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    MessageService messageService;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    ConversationMemberRepository memberRepository;
    @Autowired
    ConversationRepository conversationRepository;
    @Autowired
    UserRepository userRepository;

    @AfterEach
    void cleanDatabase() {
        List<Conversation> conversations = conversationRepository.findAll();
        conversations.forEach(conversation -> conversation.setLastMessage(null));
        conversationRepository.saveAllAndFlush(conversations);
        memberRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void tenConcurrentSendsPersistUniqueContiguousSequences() throws Exception {
        assertConcurrentSends(10);
    }

    @Test
    void twentyFiveConcurrentSendsPersistUniqueContiguousSequences() throws Exception {
        assertConcurrentSends(25);
    }

    private void assertConcurrentSends(int sendCount) throws Exception {
        User user = userRepository.save(User.create(
                "Concurrent Sender",
                "sender_" + sendCount,
                "sender-" + sendCount + "@example.com",
                "hash",
                SystemRole.USER
        ));
        Conversation conversation = conversationRepository.save(
                Conversation.create("Concurrency Test", ConversationType.GROUP, 100, null));
        memberRepository.save(ConversationMember.create(
                conversation, user, ConversationMemberRole.OWNER, JoinMethod.CREATED, null));

        ExecutorService executor = Executors.newFixedThreadPool(sendCount);
        CountDownLatch ready = new CountDownLatch(sendCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < sendCount; index++) {
                int messageNumber = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    messageService.send(
                            user.getId(),
                            conversation.getId(),
                            new CreateMessageRequest(
                                    "Concurrent message " + messageNumber,
                                    MessageType.TEXT,
                                    null,
                                    List.of()
                            )
                    );
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Send tasks did not become ready");
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<Long> sequences = messageRepository.findAll().stream()
                .filter(message -> message.getConversation().getId().equals(conversation.getId()))
                .map(Message::getSequence)
                .sorted(Comparator.naturalOrder())
                .toList();

        assertEquals(sendCount, sequences.size(), "Every successful send must be persisted");
        assertEquals(sendCount, sequences.stream().distinct().count(), "Sequences must be unique");
        assertEquals(1L, sequences.getFirst());
        assertEquals((long) sendCount, sequences.getLast());
        for (int index = 0; index < sendCount; index++) {
            assertEquals(index + 1L, sequences.get(index), "Sequences must be contiguous");
        }
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent send start was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
