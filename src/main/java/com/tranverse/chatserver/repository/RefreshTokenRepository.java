package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.RefreshToken;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.RefreshTokenRevokedReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    boolean existsByTokenHash(String refreshToken);
    Optional<RefreshToken> findByTokenHash(String refreshToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt from RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now, rt.revokedReason = :reason" +
            " WHERE rt.familyId = :familyId and rt.revokedAt IS NULL")
    void revokeActiveByFamilyId(@Param("reason")RefreshTokenRevokedReason reason,
                                @Param("familyId") UUID familyId,
                                @Param("now") Instant now);
}
