package com.tranverse.chatserver.dto.response.user;

import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.AuthProvider;
import com.tranverse.chatserver.enums.SystemRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String username,
        String email,
        String avatar,
        String phone,
        LocalDate dob,
        SystemRole role,
        AuthProvider provider,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getUsername(), user.getEmail(),
                user.getAvatar(), user.getPhone(), user.getDob(), user.getRole(),
                user.getProvider(), user.getCreatedAt()
        );
    }
}
