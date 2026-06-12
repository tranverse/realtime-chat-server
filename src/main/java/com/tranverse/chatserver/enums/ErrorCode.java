package com.tranverse.chatserver.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid email or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "Invalid token"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "Token expired"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),

    // Conversation
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"),
    FORBIDDEN_CONVERSATION(HttpStatus.FORBIDDEN, "CONVERSATION_FORBIDDEN", "You do not have permission in this conversation"),

    // Common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
