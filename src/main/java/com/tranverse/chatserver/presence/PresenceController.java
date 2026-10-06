package com.tranverse.chatserver.presence;

import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PresenceController {
    private final RedisPresenceStore store;
    private final PresenceAccess access;
    private final PresenceProperties properties;

    @GetMapping("/api/v1/users/{userId}/presence")
    public ApiResponse<PresenceState> presence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        access.requireAccess(SecurityUtils.userId(jwt), userId);
        return ApiResponse.<PresenceState>builder().code(200).message("Presence retrieved")
                .data(store.current(userId)).build();
    }

    @GetMapping("/api/v1/presence/config")
    public ApiResponse<Map<String, Long>> config() {
        return ApiResponse.<Map<String, Long>>builder().code(200).message("Presence configuration")
                .data(Map.of("heartbeatMillis", properties.heartbeatMillis())).build();
    }
}
