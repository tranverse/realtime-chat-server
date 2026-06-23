package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.PendingRegistration;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PendingRegistrationRepository extends CrudRepository<PendingRegistration, String> {
    Optional<PendingRegistration> findByEmail(String email);
    void deleteByEmail(String email);
}
