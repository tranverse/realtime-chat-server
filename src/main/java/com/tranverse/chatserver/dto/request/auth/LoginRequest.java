package com.tranverse.chatserver.dto.request.auth;

import lombok.Getter;

@Getter
public class LoginRequest {
    private String email;
    private String password;
}
