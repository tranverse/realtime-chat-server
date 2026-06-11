package com.tranverse.chatserver.entity;

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

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String avatar;

    @Column(length = 20)
    private String phone;

    private LocalDate dob;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<ConversationMember> conversationMembers = new ArrayList<>();


}
