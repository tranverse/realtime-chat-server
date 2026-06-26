package com.tranverse.chatserver.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VerifyResetCodeResponse {
    String resetToken;
    String message;
}
