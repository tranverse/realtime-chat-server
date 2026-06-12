package com.tranverse.chatserver.repository;

import com.tranverse.chatserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
