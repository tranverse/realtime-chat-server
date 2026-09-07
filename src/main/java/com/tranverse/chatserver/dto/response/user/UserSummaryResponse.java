package com.tranverse.chatserver.dto.response.user;

import com.tranverse.chatserver.entity.User;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String name,
        String username,
        String email,
        String avatar
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar()
        );
    }
}
