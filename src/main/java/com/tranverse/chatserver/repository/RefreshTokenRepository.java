package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    boolean existsByTokenHash(String refreshToken);
    Optional<RefreshToken> findByTokenHash(String refreshToken);
}
