package com.tranverse.chatserver.dto.request.user;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(min = 2, max = 120) String name,
        @Size(min = 3, max = 50) String username,
        @Size(max = 500) String avatar,
        @Size(max = 20) String phone,
        LocalDate dob
) {
}
