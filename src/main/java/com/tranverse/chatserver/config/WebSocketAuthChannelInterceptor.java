package com.tranverse.chatserver.config;

import com.tranverse.chatserver.enums.ConversationMemberStatus;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile(
            "^/topic/conversations/([0-9a-fA-F-]{36})$");

    private final JwtDecoder jwtDecoder;
    private final ConversationMemberRepository memberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing WebSocket bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
            List<String> authorityClaims = jwt.getClaimAsStringList("authorities");
            List<SimpleGrantedAuthority> authorities = authorityClaims == null
                    ? List.of()
                    : authorityClaims.stream().map(SimpleGrantedAuthority::new).toList();
            accessor.setUser(new JwtAuthenticationToken(jwt, authorities));
        } catch (JwtException exception) {
            throw new MessageDeliveryException("Invalid or expired WebSocket token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = CONVERSATION_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new MessageDeliveryException("Unauthenticated WebSocket subscription");
        }
        try {
            UUID userId = UUID.fromString(principal.getName());
            UUID conversationId = UUID.fromString(matcher.group(1));
            boolean isMember = memberRepository.existsByConversationIdAndUserIdAndStatus(
                    conversationId, userId, ConversationMemberStatus.ACTIVE);
            if (!isMember) {
                throw new MessageDeliveryException("Forbidden conversation subscription");
            }
        } catch (IllegalArgumentException exception) {
            throw new MessageDeliveryException("Invalid WebSocket identity or destination");
        }
    }
}
