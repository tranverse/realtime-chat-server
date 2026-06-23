package com.tranverse.chatserver.entity;

import com.tranverse.chatserver.enums.SystemRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(length = 120, nullable = false)
    private String name;

    @Column(length = 50, nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String avatar;

    @Column(length = 20)
    private String phone;

    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemRole role;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<ConversationMember> conversationMembers = new ArrayList<>();

    public static User create(String name,
                              String username,
                              String email,
                              String passwordHash,
                              SystemRole role) {
        User user = new User();
        user.name = name;
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = role;
        return user;
    }
}
