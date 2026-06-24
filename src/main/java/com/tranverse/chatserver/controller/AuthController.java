package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.auth.*;
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
                        .code(HttpStatus.OK.value())
                        .message("Refresh token successfully")
                        .data(authService.refreshToken(request))
                        .build()
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout")
    public ResponseEntity<ApiResponse<MessageResponse>> logout(@Valid @RequestBody LogoutRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<MessageResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Logout successfully")
                        .data(authService.logout(request))
                        .build()
        );
    }

    @PostMapping("/register/resend")
    @Operation(summary = "Resend register code", description = "Resend verification code to user's email for completing registration")
    public ResponseEntity<ApiResponse<MessageResponse>> resendRegisterCode(@RequestBody @Valid ResendRegisterCodeRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<MessageResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Resend code successfully")
                        .data(authService.resendRegisterCode(request))
                        .build()
        );
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Send OTP code to user's email to reset password")
    public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<MessageResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("OTP sent successfully")
                        .data(authService.sendForgotPasswordCode(request))
                        .build()
        );
    }

    @PostMapping("/forgot-password/verify")
    @Operation(summary = "Verify reset code", description = "Verify OTP code sent to email for password reset")
    public ResponseEntity<ApiResponse<MessageResponse>> verifyResetCode(@RequestBody @Valid VerifyResetCodeRequest request) {

        return ResponseEntity.ok(
                ApiResponse.<MessageResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Reset code successfully")
                        .data(authService.verifyResetCode(request))
                        .build()
        );
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset user's password after OTP verification")
    public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<MessageResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Reset password successfully")
                        .data(authService.resetPassword(request))
                        .build()
        );
    }
}
