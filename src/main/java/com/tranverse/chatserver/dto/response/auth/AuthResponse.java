package com.tranverse.chatserver.dto.response.auth;

import lombok.*;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
}
