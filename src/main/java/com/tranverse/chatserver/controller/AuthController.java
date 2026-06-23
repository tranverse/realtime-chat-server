package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.auth.LoginRequest;
import com.tranverse.chatserver.dto.request.auth.RefreshTokenRequest;
import com.tranverse.chatserver.dto.request.auth.RegisterRequest;
import com.tranverse.chatserver.dto.request.auth.VerifyRegisterRequest;
import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.dto.response.auth.MessageResponse;
import com.tranverse.chatserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "API login/register")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Login by email and password, and return accessToken and RefreshToken")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .data(authService.login(loginRequest))
                .code(HttpStatus.OK.value())
                .message("Login successfully")
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Send register verification code",description = "Send verification code to user's email")
    public ResponseEntity<ApiResponse<MessageResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        ApiResponse<MessageResponse> response = ApiResponse.<MessageResponse>builder()
                .data(authService.sendRegisterCode(registerRequest))
                .code(HttpStatus.OK.value())
                .message("Verification code sent successfully")
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/verify")
    @Operation(summary = "Verify register code", description = "Verify email code and create account")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegister(@Valid @RequestBody VerifyRegisterRequest verifyRegisterRequest) {
        ApiResponse<AuthResponse> response =
                ApiResponse.<AuthResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Register successfully")
                        .data(authService.verifyRegisterCode(verifyRegisterRequest))
                        .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Generate new access token and refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .code(200)
                        .message("Refresh token successfully")
                        .data(authService.refreshToken(request))
                        .build()
        );
    }
}
