package com.tranverse.chatserver.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ResendRegisterCodeRequest {
    @Email
    @NotBlank
    private String email;
}
