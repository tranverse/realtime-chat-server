package com.tranverse.chatserver.dto.response.auth;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
}
