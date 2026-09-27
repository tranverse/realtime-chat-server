package com.tranverse.chatserver.service;

import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.RefreshTokenRevokedReason;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import com.tranverse.chatserver.security.jwt.JwtProperties;
import com.tranverse.chatserver.security.jwt.JwtService;
import com.tranverse.chatserver.utils.HashTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final String HASH = "token-hash";

    @Mock
    JwtService jwtService;
    @Mock
    RefreshTokenRepository repository;
    @Mock
    HashTokenUtil hashTokenUtil;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(
                jwtService,
                repository,
                new JwtProperties("access", "refresh", 15, 30),
                hashTokenUtil);
        when(hashTokenUtil.sha256(RAW_TOKEN)).thenReturn(HASH);
    }

    @Test
    void validTokenCanBeRotated() {
        RefreshToken token = activeToken();
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertEquals(token, service.verifyForRotation(RAW_TOKEN));
        verify(jwtService).verifyRefreshToken(RAW_TOKEN);
    }

    @Test
    void expiredTokenIsRevokedAndRejected() {
        RefreshToken token = activeToken();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.verifyForRotation(RAW_TOKEN));

        assertEquals(ErrorCode.TOKEN_EXPIRED, exception.getErrorCode());
        assertEquals(RefreshTokenRevokedReason.EXPIRED, token.getRevokedReason());
        assertNotNull(token.getRevokedAt());
        verify(repository).save(token);
    }

    @Test
    void reuseOfRotatedTokenRevokesItsActiveFamily() {
        RefreshToken token = activeToken();
        token.setRevokedAt(Instant.now());
        token.setRevokedReason(RefreshTokenRevokedReason.ROTATED);
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.verifyForRotation(RAW_TOKEN));

        assertEquals(ErrorCode.TOKEN_REUSE_DETECTED, exception.getErrorCode());
        verify(repository).revokeActiveByFamilyId(
                org.mockito.ArgumentMatchers.eq(RefreshTokenRevokedReason.REUSE_DETECTED),
                org.mockito.ArgumentMatchers.eq(token.getFamilyId()),
                any(Instant.class));
    }

    @Test
    void logoutRevokesAnActiveToken() {
        RefreshToken token = activeToken();
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

        service.revoke(RAW_TOKEN, RefreshTokenRevokedReason.LOGOUT);

        assertTrue(token.isRevoked());
        assertEquals(RefreshTokenRevokedReason.LOGOUT, token.getRevokedReason());
        verify(repository).save(token);
    }

    private RefreshToken activeToken() {
        User user = User.create("Test User", "test_user", "test@example.com", "hash", SystemRole.USER);
        return RefreshToken.builder()
                .user(user)
                .familyId(UUID.randomUUID())
                .tokenHash(HASH)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
