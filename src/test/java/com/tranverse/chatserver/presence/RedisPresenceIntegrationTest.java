package com.tranverse.chatserver.presence;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RedisPresenceIntegrationTest {
    @Container static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static LettuceConnectionFactory factory;
    static RedisMessageListenerContainer listener;
    static StringRedisTemplate redis;
    static final LinkedBlockingQueue<PresenceState> events = new LinkedBlockingQueue<>();
    RedisPresenceStore store;
    UUID user;

    @BeforeAll static void connectRedis() {
        factory = new LettuceConnectionFactory(redisContainer.getHost(), redisContainer.getMappedPort(6379));
        factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory);
        listener = new RedisMessageListenerContainer(); listener.setConnectionFactory(factory);
        ObjectMapper mapper = new ObjectMapper();
        listener.addMessageListener((message, pattern) -> events.add(mapper.readValue(message.getBody(), PresenceState.class)),
                new ChannelTopic(RedisPresenceStore.CHANNEL));
        listener.afterPropertiesSet(); listener.start();
    }
    @AfterAll static void closeRedis() throws Exception { if (listener != null) listener.destroy(); if (factory != null) factory.destroy(); }
    @BeforeEach void setup() { user = UUID.randomUUID(); store = new RedisPresenceStore(redis, new PresenceProperties(1_000, 1_300, 100)); }

    @Test void firstSessionConnectsAndLastDisconnects() throws Exception {
        assertTrue(store.touch(user, "laptop").online());
        assertEquals(new PresenceState(user, true), nextEvent());
        assertFalse(store.remove(user, "laptop").online());
        assertEquals(new PresenceState(user, false), nextEvent());
        assertNoEvent();
    }

    @Test void twoTabsAndDevicesRemainOnlineUntilFinalSessionDisconnects() throws Exception {
        store.touch(user, "laptop"); nextEvent();
        store.touch(user, "phone"); assertNoEvent();
        assertTrue(store.remove(user, "laptop").online()); assertNoEvent();
        assertFalse(store.remove(user, "phone").online());
        assertEquals(new PresenceState(user, false), nextEvent());
        store.remove(user, "phone"); assertNoEvent();
    }

    @Test void heartbeatRefreshesExpiryWithoutPublishingTransition() throws Exception {
        store.touch(user, "tab"); nextEvent();
        Thread.sleep(800);
        store.touch(user, "tab"); assertNoEvent();
        Thread.sleep(450);
        assertTrue(store.current(user).online());
        assertEquals(1, redis.opsForZSet().zCard(RedisPresenceStore.PREFIX + "user:" + user));
    }

    @Test void unexpectedNetworkLossExpiresAndSweeperPublishesOfflineOnce() throws Exception {
        store.touch(user, "tab"); nextEvent(); Thread.sleep(1_400);
        store.expireSessions();
        assertEquals(new PresenceState(user, false), nextEvent());
        assertFalse(store.current(user).online());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisPresenceStore.PREFIX + "user:" + user)));
        store.expireSessions(); assertNoEvent();
    }

    @Test void expiredSessionDoesNotHideAnotherLiveSession() throws Exception {
        store.touch(user, "old"); nextEvent(); Thread.sleep(800);
        store.touch(user, "live"); Thread.sleep(550);
        assertTrue(store.current(user).online()); assertNoEvent();
        assertEquals(1, redis.opsForZSet().zCard(RedisPresenceStore.PREFIX + "user:" + user));
    }

    @Test void reconnectRegistersNewSessionAndStaleDisconnectCannotRemoveIt() throws Exception {
        store.touch(user, "old"); nextEvent(); store.remove(user, "old"); nextEvent();
        assertTrue(store.touch(user, "new").online()); nextEvent();
        assertTrue(store.remove(user, "old").online()); assertNoEvent();
    }

    @Test void simultaneousConnectsAndDisconnectsPublishOnlyEffectiveTransitions() throws Exception {
        store = new RedisPresenceStore(redis, new PresenceProperties(1_000, 30_000, 100));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Callable<PresenceState>> connects = new ArrayList<>();
            for (int i = 0; i < 8; i++) { String session = "session-" + i; connects.add(() -> store.touch(user, session)); }
            for (var result : executor.invokeAll(connects)) assertTrue(result.get().online());
            assertEquals(new PresenceState(user, true), nextEvent()); assertNoEvent();
            List<java.util.concurrent.Callable<PresenceState>> disconnects = new ArrayList<>();
            for (int i = 0; i < 8; i++) { String session = "session-" + i; disconnects.add(() -> store.remove(user, session)); }
            for (var result : executor.invokeAll(disconnects)) result.get();
            assertEquals(new PresenceState(user, false), nextEvent()); assertNoEvent();
        }
    }

    @Test void existingOtpAndOAuthKeysAreUntouched() {
        redis.opsForValue().set("otp:test-email", "otp-value");
        redis.opsForValue().set("oauth2:exchange:test", "exchange-value");
        store.touch(user, "tab"); store.remove(user, "tab"); store.expireSessions();
        assertEquals("otp-value", redis.opsForValue().get("otp:test-email"));
        assertEquals("exchange-value", redis.opsForValue().get("oauth2:exchange:test"));
    }

    private PresenceState nextEvent() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            PresenceState event = events.poll(100, TimeUnit.MILLISECONDS);
            if (event != null && event.userId().equals(user)) return event;
        }
        fail("No transition event for " + user); return null;
    }
    private void assertNoEvent() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(150);
        while (System.nanoTime() < deadline) {
            PresenceState event = events.poll(20, TimeUnit.MILLISECONDS);
            if (event != null && event.userId().equals(user)) fail("Unexpected presence transition: " + event);
        }
    }
}
