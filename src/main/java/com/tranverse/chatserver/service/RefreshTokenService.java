package com.tranverse.chatserver.service;

import com.nimbusds.jwt.SignedJWT;
import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.RefreshTokenRevokedReason;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final HashTokenUtil hashTokenUtil;
    public String createAndSave(User user) {
        return createAndSave(user, UUID.randomUUID());
    }

    public String createAndSave(User user, UUID familyId) {
        String token = jwtService.generateRefreshToken(user.getId().toString());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiresAt(Instant.now().plus(jwtProperties.refreshExpirationDays(), ChronoUnit.DAYS))
                .familyId(familyId)
                .tokenHash(hashTokenUtil.sha256(token))
                .revokedAt(null)
                .revokedReason(null)
                .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    @Transactional
    public RefreshToken consumeForRotation(String token) {
        jwtService.verifyRefreshToken(token);
        String hash = hashTokenUtil.sha256(token);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if(refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshToken.setRevokedAt(Instant.now());
            refreshToken.setRevokedReason(RefreshTokenRevokedReason.EXPIRED);
            refreshTokenRepository.save(refreshToken);

            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        if(refreshToken.isRevoked()){
            if(RefreshTokenRevokedReason.ROTATED.equals(refreshToken.getRevokedReason())){
                refreshTokenRepository.revokeActiveByFamilyId(RefreshTokenRevokedReason.REUSE_DETECTED, refreshToken.getFamilyId(), Instant.now());
            }
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        refreshToken.setRevokedAt(Instant.now());
        refreshToken.setRevokedReason(RefreshTokenRevokedReason.ROTATED);
        refreshTokenRepository.save(refreshToken);
        return refreshToken;
    }

    public RefreshToken verify(String token) {

        jwtService.verifyRefreshToken(token);

        String hash = hashTokenUtil.sha256(token);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash)
                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if(refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }
        if (refreshToken.isRevoked()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        return refreshToken;
    }

    @Transactional
    public void revoke(String token, RefreshTokenRevokedReason revokedReason) {
        String hash = hashTokenUtil.sha256(token);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));
        if(!refreshToken.isRevoked()) {
            refreshToken.setRevokedAt(Instant.now());
            refreshToken.setRevokedReason(revokedReason);
            refreshTokenRepository.save(refreshToken);
        }
    }
}