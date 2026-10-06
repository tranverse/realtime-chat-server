package com.tranverse.chatserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PresenceLifecycle {
    private final RedisPresenceStore store;

    @EventListener
    public void connected(SessionConnectedEvent event) {
        String session = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (event.getUser() != null && session != null) store.touch(UUID.fromString(event.getUser().getName()), session);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event.getUser() != null) store.remove(UUID.fromString(event.getUser().getName()), event.getSessionId());
    }

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal, SimpMessageHeaderAccessor headers) {
        // Identity/session come from authenticated STOMP headers, never the payload.
        if (principal != null && headers.getSessionId() != null) {
            store.touch(UUID.fromString(principal.getName()), headers.getSessionId());
        }
    }

    @Scheduled(fixedDelayString = "${app.presence.sweep-millis:5000}")
    public void sweep() { store.expireSessions(); }
}
