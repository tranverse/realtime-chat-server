package com.tranverse.chatserver.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class VerifyRegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid Email")
    @Pattern(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "Invalid email format"
    )
    private String email;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must include 6 characters")
    private String code;
}
