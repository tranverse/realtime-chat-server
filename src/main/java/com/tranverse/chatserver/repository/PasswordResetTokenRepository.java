package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByEmail(String email);

    void deleteByEmail(String email);
}
