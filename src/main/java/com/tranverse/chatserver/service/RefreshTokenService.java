package com.tranverse.chatserver.service;

import com.nimbusds.jwt.SignedJWT;
import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import com.tranverse.chatserver.security.jwt.JwtProperties;
import com.tranverse.chatserver.security.jwt.JwtService;
import com.tranverse.chatserver.utils.HashTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final HashTokenUtil hashTokenUtil;

    public String createAndSave(User user) {
        String token = jwtService.generateRefreshToken(user.getId().toString());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiresAt(Instant.now().plus(jwtProperties.refreshExpirationDays(), ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public RefreshToken verify(String token) {

        jwtService.verifyRefreshToken(token);

        String hash = hashTokenUtil.sha256(token);

        RefreshToken refreshToken =
                refreshTokenRepository.findByTokenHash(hash)
                        .orElseThrow(() ->
                                new AppException(ErrorCode.INVALID_TOKEN));

        if (refreshToken.isRevoked()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        return refreshToken;
    }

    @Transactional
    public void revoke(String token) {
        String hash = hashTokenUtil.sha256(token);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);
    }
}