package com.tranverse.chatserver.config;

import com.tranverse.chatserver.enums.ConversationMemberStatus;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {
    @Mock
    JwtDecoder jwtDecoder;
    @Mock
    ConversationMemberRepository memberRepository;
    @InjectMocks
    WebSocketAuthChannelInterceptor interceptor;

    @Test
    void connectRequiresBearerToken() {
        MessageDeliveryException exception = assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(message(StompCommand.CONNECT, null, null, null), ignoredChannel()));

        assertEquals("Missing WebSocket bearer token", exception.getMessage());
    }

    @Test
    void connectRejectsExpiredOrInvalidToken() {
        when(jwtDecoder.decode("invalid")).thenThrow(new JwtException("expired"));

        MessageDeliveryException exception = assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(
                        message(StompCommand.CONNECT, null, "Bearer invalid", null), ignoredChannel()));

        assertEquals("Invalid or expired WebSocket token", exception.getMessage());
    }

    @Test
    void connectAuthenticatesValidJwtAndAuthorities() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("valid")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("authorities", List.of("ROLE_USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode("valid")).thenReturn(jwt);
        Message<byte[]> message = message(StompCommand.CONNECT, null, "Bearer valid", null);

        interceptor.preSend(message, ignoredChannel());

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        JwtAuthenticationToken authentication = assertInstanceOf(
                JwtAuthenticationToken.class,
                accessor.getUser());
        assertEquals(userId.toString(), authentication.getName());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void conversationSubscriptionRequiresAuthenticatedPrincipal() {
        String destination = "/topic/conversations/" + UUID.randomUUID();

        assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SUBSCRIBE, destination, null, null), ignoredChannel()));
    }

    @Test
    void nonMemberCannotSubscribeToConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(memberRepository.existsByConversationIdAndUserIdAndStatus(
                conversationId, userId, ConversationMemberStatus.ACTIVE)).thenReturn(false);

        assertThrows(
                MessageDeliveryException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SUBSCRIBE, "/topic/conversations/" + conversationId, null,
                                authentication(userId)), ignoredChannel()));
    }

    @Test
    void activeMemberCanSubscribeToConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(memberRepository.existsByConversationIdAndUserIdAndStatus(
                conversationId, userId, ConversationMemberStatus.ACTIVE)).thenReturn(true);

        assertDoesNotThrow(() -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/conversations/" + conversationId, null,
                        authentication(userId)), ignoredChannel()));
    }

    private JwtAuthenticationToken authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("valid")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            String authorization,
            JwtAuthenticationToken authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (authorization != null) accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        if (authentication != null) accessor.setUser(authentication);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private org.springframework.messaging.MessageChannel ignoredChannel() {
        return mock(org.springframework.messaging.MessageChannel.class);
    }
}
