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
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "Unauthenticated"),
    REGISTER_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_REGISTER_CODE_NOT_FOUND", "Register code not found"),
    REGISTER_CODE_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_REGISTER_CODE_EXPIRED", "Register code expired"),
    REGISTER_CODE_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "AUTH_REGISTER_CODE_TOO_MANY_ATTEMPTS", "Too many attempts"),
    INVALID_REGISTER_CODE(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REGISTER_CODE", "Invalid register code"),
    INVALID_OAUTH2_USER(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_OAUTH2_USER", "Invalid oauth2 user"),
    GOOGLE_EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "GOOGLE_EMAIL_NOT_VERIFIED", "Google account email is not verified"),    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Username already exists"),

    // Conversation
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"),
    FORBIDDEN_CONVERSATION(HttpStatus.FORBIDDEN, "CONVERSATION_FORBIDDEN", "You do not have permission in this conversation"),
    INVALID_CONVERSATION(HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION", "Invalid conversation data"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Conversation member not found"),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER_ALREADY_EXISTS", "User is already a conversation member"),
    OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "OWNER_CANNOT_LEAVE", "Owner must transfer ownership before leaving"),
    MEMBER_LIMIT_REACHED(HttpStatus.CONFLICT, "MEMBER_LIMIT_REACHED", "Conversation member limit reached"),
    INVITE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE_LINK_NOT_FOUND", "Invite link not found"),
    INVITE_LINK_UNAVAILABLE(HttpStatus.GONE, "INVITE_LINK_UNAVAILABLE", "Invite link is expired or revoked"),
    JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "JOIN_REQUEST_NOT_FOUND", "Join request not found"),
    JOIN_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "JOIN_REQUEST_ALREADY_EXISTS", "A pending join request already exists"),

    // Message
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message not found"),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Message content is required"),

    // Common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error"),

    // OTP
    OTP_RESEND_TOO_FAST(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_FAST", "Please wait before requesting another OTP"),
    OTP_SEND_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "OTP_SEND_TOO_MANY_REQUESTS", "Too many OTP requests. Please try again later"),
    OTP_VERIFY_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "OTP_VERIFY_TOO_MANY_ATTEMPTS", "Too many incorrect OTP verification attempts. Please try again later."),
    RESET_CODE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "RESET_CODE_NOT_VERIFIED", "Reset code has not been verified."),
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "TOKEN_REUSE_DETECTED", "Refresh token reuse detected. Please log in again."),
            ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
