package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);

    @Query("""
            select u from User u
            where u.deletedAt is null
              and u.id <> :currentUserId
              and (lower(u.name) like lower(concat('%', :query, '%'))
                   or lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            order by u.name asc
            """)
    Page<User> searchActiveUsers(@Param("currentUserId") UUID currentUserId,
                                 @Param("query") String query,
                                 Pageable pageable);
}
