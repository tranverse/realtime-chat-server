package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.auth.*;
import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.dto.response.auth.MessageResponse;
import com.tranverse.chatserver.entity.PasswordResetToken;
import com.tranverse.chatserver.entity.PendingRegistration;
import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.PasswordResetTokenRepository;
import com.tranverse.chatserver.repository.PendingRegistrationRepository;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import com.tranverse.chatserver.repository.UserRepository;
import com.tranverse.chatserver.security.jwt.JwtService;
import com.tranverse.chatserver.security.user.UserPrincipal;
import com.tranverse.chatserver.utils.HashTokenUtil;
import com.tranverse.chatserver.utils.OtpUtils;
import com.tranverse.chatserver.utils.UsernameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HashTokenUtil hashTokenUtil;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private static final int CODE_EXPIRED_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.id()).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        String accessToken = jwtService.generateAccessToken(principal.id().toString(), principal.role());
        String refreshToken = refreshTokenService.createAndSave(user);

        return new AuthResponse(accessToken, refreshToken);
    }

    public MessageResponse sendRegisterCode(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String code = OtpUtils.generateCode();

        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByEmail(registerRequest.getEmail())
                .orElseGet(PendingRegistration::new);


        pendingRegistration.setEmail(registerRequest.getEmail());
        pendingRegistration.setName(registerRequest.getName());
        pendingRegistration.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        pendingRegistration.setCodeHash(passwordEncoder.encode(code));
        pendingRegistration.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRED_MINUTES));
        pendingRegistration.setAttempts(0);
        pendingRegistrationRepository.save(pendingRegistration);

        mailService.sendRegisterCode(registerRequest.getEmail(), code);
        return new MessageResponse("Verification code sent to email");
    }

    public AuthResponse verifyRegisterCode(VerifyRegisterRequest verifyRegisterRequest) {
        if(userRepository.existsByEmail(verifyRegisterRequest.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByEmail(verifyRegisterRequest.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_CODE_NOT_FOUND));

        if(pendingRegistration.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new AppException(ErrorCode.REGISTER_CODE_EXPIRED);
        }

        if(pendingRegistration.getAttempts() >= MAX_ATTEMPTS) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new AppException(ErrorCode.REGISTER_CODE_TOO_MANY_ATTEMPTS);
        }
        boolean matched = passwordEncoder.matches(verifyRegisterRequest.getCode(), pendingRegistration.getCodeHash());
        if(!matched) {
            pendingRegistration.setAttempts(pendingRegistration.getAttempts() + 1);
            pendingRegistrationRepository.save(pendingRegistration);
            throw new AppException(ErrorCode.INVALID_REGISTER_CODE);
        }

        User user = User.create(pendingRegistration.getName(),
                UsernameUtils.generate(pendingRegistration.getEmail()),
                pendingRegistration.getEmail(),
                pendingRegistration.getPassword(),
                SystemRole.USER);
        user = userRepository.save(user);
        pendingRegistrationRepository.delete(pendingRegistration);

        String accessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole().toString());
        String refreshToken = refreshTokenService.createAndSave(user);

        return new AuthResponse(accessToken, refreshToken);
    }
    // Rotation Refresh token
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        RefreshToken refreshToken = refreshTokenService.verify(refreshTokenRequest.getRefreshToken());

        User user = refreshToken.getUser();

        refreshTokenService.revoke(refreshTokenRequest.getRefreshToken());

        String accessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole().toString());
        String newRefreshToken = refreshTokenService.createAndSave(user);
        return new AuthResponse(accessToken, newRefreshToken);
    }

    public MessageResponse logout(LogoutRequest logoutRequest) {
        String hash = hashTokenUtil.sha256(logoutRequest.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash).orElseThrow(
                () -> new AppException(ErrorCode.INVALID_TOKEN)
        );

        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        return new MessageResponse("Logout successful");
    }

    public MessageResponse resendRegisterCode(ResendRegisterCodeRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String code = OtpUtils.generateCode();

        PendingRegistration pending = pendingRegistrationRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_CODE_NOT_FOUND));

        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRED_MINUTES));
        pending.setAttempts(0);

        pendingRegistrationRepository.save(pending);

        mailService.sendRegisterCode(request.getEmail(), code);

        return new MessageResponse("Resend verification code successfully");
    }

    public MessageResponse sendForgotPasswordCode(ForgotPasswordRequest forgotPasswordRequest) {
        User user = userRepository.findByEmail(forgotPasswordRequest.getEmail()).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );

        String code = OtpUtils.generateCode();

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByEmail(user.getEmail())
                .orElse(new PasswordResetToken());

        passwordResetToken.setEmail(forgotPasswordRequest.getEmail());
        passwordResetToken.setAttempts(0);
        passwordResetToken.setCodeHash(passwordEncoder.encode(code));
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRED_MINUTES));
        passwordResetTokenRepository.save(passwordResetToken);
        mailService.sendRegisterCode(forgotPasswordRequest.getEmail(), code);
        return new MessageResponse("Forgot password code successfully");
    }

    public MessageResponse verifyResetCode(VerifyResetCodeRequest request) {

        PasswordResetToken token = passwordResetTokenRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        boolean match = passwordEncoder.matches(request.getCode(), token.getCodeHash());

        if (!match) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        return new MessageResponse("Code verified successfully");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.deleteByEmail(request.getEmail());

        return new MessageResponse("Password reset successfully");
    }
}
