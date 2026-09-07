package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.auth.*;
import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.dto.response.auth.MessageResponse;
import com.tranverse.chatserver.dto.response.auth.VerifyResetCodeResponse;
import com.tranverse.chatserver.entity.PasswordResetToken;
import com.tranverse.chatserver.entity.PendingRegistration;
import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.*;
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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
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
    private final OtpRateLimiterService otpRateLimiterService;

    private static final int CODE_EXPIRED_MINUTES = 2;
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

    public MessageResponse sendRegisterCode(RegisterRequest registerRequest, String ip) {
        otpRateLimiterService.checkSendLimit(OtpPurpose.REGISTER, registerRequest.getEmail(), ip);

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

    public AuthResponse verifyRegisterCode(VerifyRegisterRequest verifyRegisterRequest, String ip) {
        otpRateLimiterService.checkVerifyLimit(
                OtpPurpose.REGISTER,
                verifyRegisterRequest.getEmail(),
                ip
        );

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

        otpRateLimiterService.clearVerifyLimit(
                OtpPurpose.REGISTER,
                verifyRegisterRequest.getEmail(),
                ip
        );

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
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        RefreshToken oldRefreshToken  = refreshTokenService.verifyForRotation(refreshTokenRequest.getRefreshToken());

        User user = oldRefreshToken .getUser();

        refreshTokenService.revokeAsRotated(oldRefreshToken);

        String accessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole().toString());
        String newRefreshToken = refreshTokenService.createAndSave(user, oldRefreshToken.getFamilyId());

        return new AuthResponse(accessToken, newRefreshToken);
    }

    @Transactional
    public MessageResponse logout(LogoutRequest logoutRequest) {
        refreshTokenService.revoke(
                logoutRequest.getRefreshToken(),
                RefreshTokenRevokedReason.LOGOUT
        );

        return new MessageResponse("Logout successful");
    }

    @Transactional
    public MessageResponse logoutAllDevices(UserPrincipal userPrincipal) {
        return logoutAllDevices(userPrincipal.id());
    }

    @Transactional
    public MessageResponse logoutAllDevices(UUID userId) {
        refreshTokenService.revokeAllByUserId(userId, RefreshTokenRevokedReason.LOGOUT_ALL);
        return new MessageResponse("Logged out from all devices successfully");
    }

    public MessageResponse resendRegisterCode(ResendRegisterCodeRequest request, String ip) {
        otpRateLimiterService.checkSendLimit(OtpPurpose.REGISTER, request.getEmail(), ip);
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

    public MessageResponse sendForgotPasswordCode(ForgotPasswordRequest forgotPasswordRequest, String ip) {
        otpRateLimiterService.checkSendLimit(
                OtpPurpose.RESET_PASSWORD,
                forgotPasswordRequest.getEmail(),
                ip
        );

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

        passwordResetToken.setVerifiedAt(null);
        passwordResetToken.setResetTokenHash(null);
        passwordResetToken.setResetTokenExpiresAt(null);

        passwordResetTokenRepository.save(passwordResetToken);
        mailService.sendRegisterCode(forgotPasswordRequest.getEmail(), code);
        return new MessageResponse("Forgot password code successfully");
    }

    @Transactional
    public VerifyResetCodeResponse verifyResetCode(VerifyResetCodeRequest request, String ip) {
        otpRateLimiterService.checkVerifyLimit(
                OtpPurpose.RESET_PASSWORD,
                request.getEmail(),
                ip
        );
        PasswordResetToken token = passwordResetTokenRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(token);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        if (token.getAttempts() >= MAX_ATTEMPTS) {
            passwordResetTokenRepository.delete(token);
            throw new AppException(ErrorCode.OTP_VERIFY_TOO_MANY_ATTEMPTS);
        }


        boolean match = passwordEncoder.matches(request.getCode(), token.getCodeHash());

        if (!match) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        String resetToken = UUID.randomUUID().toString();

        token.setVerifiedAt(LocalDateTime.now());
        token.setResetTokenHash(hashTokenUtil.sha256(resetToken));
        token.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(10));
        passwordResetTokenRepository.save(token);

        otpRateLimiterService.clearVerifyLimit(
                OtpPurpose.RESET_PASSWORD,
                request.getEmail(),
                ip
        );


        return new VerifyResetCodeResponse(resetToken, "Code verified successfully");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new AppException(ErrorCode.INVALID_TOKEN)
        );
        if(token.getVerifiedAt() == null) {
            throw new AppException(ErrorCode.RESET_CODE_NOT_VERIFIED);
        }

        if (token.getResetTokenExpiresAt() == null || token.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(token);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        String requestResetToken = hashTokenUtil.sha256(request.getResetToken());

        if(!requestResetToken.equals(token.getResetTokenHash())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.deleteByEmail(request.getEmail());

        refreshTokenService.revokeAllByUserId(user.getId(), RefreshTokenRevokedReason.PASSWORD_RESET);

        return new MessageResponse("Password reset successfully");
    }

   @Transactional
   public AuthResponse loginWithGoogle(OAuth2User oAuth2User){
        String providerId = getRequiredAttribute(oAuth2User, "sub");
        String email = getRequiredAttribute(oAuth2User, "email").toLowerCase(Locale.ROOT);

        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        if(!Boolean.TRUE.equals(emailVerified)) {
            throw new AppException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
        }

        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .or(() -> userRepository.findByEmail(email))
                .map(existingUser -> {
                    existingUser.setProviderId(providerId);
                    existingUser.setProvider(AuthProvider.GOOGLE);

                    if(name != null && !name.isBlank()) {
                        existingUser.setName(name);
                    }

                    if(picture != null && !picture.isBlank()) {
                        existingUser.setAvatar(picture);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    String encodedRandomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

                    User newUser = User.create(
                            name != null && !name.isBlank() ? name : email,
                            UsernameUtils.generate(email),
                            email,
                            encodedRandomPassword,
                            SystemRole.USER
                    );
                    newUser.setProviderId(providerId);
                    newUser.setProvider(AuthProvider.GOOGLE);
                    newUser.setAvatar(picture);
                    return  userRepository.save(newUser);
                });
       String accessToken = jwtService.generateAccessToken(user.getId().toString(), user.getRole().toString());
       String refreshToken = refreshTokenService.createAndSave(user);
       return new AuthResponse(accessToken, refreshToken);
   }

    private String getRequiredAttribute(OAuth2User oauth2User, String attributeName) {
        Object value = oauth2User.getAttribute(attributeName);

        if (value == null || value.toString().isBlank()) {
            throw new AppException(ErrorCode.INVALID_OAUTH2_USER);
        }

        return value.toString();
    }
}
