package com.tranverse.chatserver.integration;

import com.tranverse.chatserver.dto.request.message.ReadConversationRequest;
import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationType;
import com.tranverse.chatserver.enums.JoinMethod;
import com.tranverse.chatserver.enums.MessageType;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import com.tranverse.chatserver.repository.ConversationRepository;
import com.tranverse.chatserver.repository.MessageRepository;
import com.tranverse.chatserver.repository.UserRepository;
import com.tranverse.chatserver.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReadReceiptConcurrencyIntegrationTest {

    @Autowired
    MessageService messageService;
    @Autowired
    ConversationMemberRepository memberRepository;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    ConversationRepository conversationRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MockMvc mvc;

    private User user;
    private Conversation conversation;
    private ConversationMember member;
    private Message message50;
    private Message message100;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        user = userRepository.save(User.create(
                "Read Test", "read_test", "read-test@example.com", "hash", SystemRole.USER));
        conversation = conversationRepository.save(
                Conversation.create("Read Test", ConversationType.GROUP, 10, null));
        member = memberRepository.save(ConversationMember.create(
                conversation, user, ConversationMemberRole.MEMBER, JoinMethod.CREATED, null));
        message50 = messageRepository.save(
                Message.create("50", MessageType.TEXT, 50L, conversation, user, null));
        message100 = messageRepository.save(
                Message.create("100", MessageType.TEXT, 100L, conversation, user, null));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void marking50Then100EndsAt100() {
        mark(message50);
        mark(message100);

        assertEquals(message100.getId(), persistedLastReadMessageId());
    }

    @Test
    void marking100Then50DoesNotRegress() {
        mark(message100);
        mark(message50);

        assertEquals(message100.getId(), persistedLastReadMessageId());
    }

    @Test
    void duplicateMarkReadIsIdempotent() {
        mark(message100);
        mark(message100);

        assertEquals(message100.getId(), persistedLastReadMessageId());
    }

    @Test
    void reopeningHistoryWithDeletedNewestMessageCanMarkItsPositionRead() {
        messageService.delete(user.getId(), message100.getId());

        // History deliberately retains a tombstone, which the client uses as its newest ID.
        assertEquals(message100.getId(), messageService.getHistory(
                user.getId(), conversation.getId(), null, 50).items().getFirst().id());
        mark(message100);
        mark(message100);

        assertEquals(message100.getId(), persistedLastReadMessageId());
        // A later, older read must not regress the watermark after the deletion.
        mark(message50);
        assertEquals(message100.getId(), persistedLastReadMessageId());
    }

    @Test
    void deletedMessageFromAnotherConversationCannotBeUsedAsReadWatermark() {
        Conversation other = conversationRepository.save(
                Conversation.create("Other", ConversationType.GROUP, 10, null));
        Message foreign = Message.create("Private", MessageType.TEXT, 200L, other, user, null);
        foreign.softDelete();
        messageRepository.save(foreign);
        mark(message50);

        AppException error = assertThrows(AppException.class, () -> mark(foreign));

        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, error.getErrorCode());
        assertEquals(message50.getId(), persistedLastReadMessageId());
    }

    @Test
    void unknownMessageCannotBeUsedAsReadWatermark() {
        mark(message50);

        AppException error = assertThrows(AppException.class, () -> messageService.markRead(
                user.getId(), conversation.getId(),
                new ReadConversationRequest(java.util.UUID.randomUUID())));

        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, error.getErrorCode());
        assertEquals(message50.getId(), persistedLastReadMessageId());
    }

    @Test
    void restReadEndpointAcceptsDeletedMessageWhenAllMessagesAreDeleted() throws Exception {
        messageService.delete(user.getId(), message50.getId());
        messageService.delete(user.getId(), message100.getId());

        mvc.perform(post("/api/v1/conversations/{id}/read", conversation.getId())
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + message100.getId() + "\"}"))
                .andExpect(status().isOk());

        assertEquals(message100.getId(), persistedLastReadMessageId());
    }

    @Test
    void concurrentUpdatesCannotMoveWatermarkBackwards() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> low = executor.submit(() -> markWhenReleased(message50, ready, start));
            Future<?> high = executor.submit(() -> markWhenReleased(message100, ready, start));

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent mark-read tasks did not become ready");
            }
            start.countDown();
            low.get(10, TimeUnit.SECONDS);
            high.get(10, TimeUnit.SECONDS);

            assertEquals(message100.getId(), persistedLastReadMessageId());
        } finally {
            executor.shutdownNow();
        }
    }

    private void mark(Message message) {
        messageService.markRead(
                user.getId(), conversation.getId(), new ReadConversationRequest(message.getId()));
    }

    private void markWhenReleased(Message message, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent mark-read start was not released");
            }
            mark(message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private java.util.UUID persistedLastReadMessageId() {
        return memberRepository.findById(member.getId())
                .orElseThrow()
                .getLastReadMessage()
                .getId();
    }

    private void cleanDatabase() {
        memberRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }
}
