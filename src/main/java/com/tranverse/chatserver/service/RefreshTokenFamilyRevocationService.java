package com.tranverse.chatserver.service;

import com.tranverse.chatserver.enums.RefreshTokenRevokedReason;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenFamilyRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeActiveFamilyForReuse(UUID familyId) {
        refreshTokenRepository.revokeActiveByFamilyId(
                RefreshTokenRevokedReason.REUSE_DETECTED,
                familyId,
                Instant.now());
    }
}
