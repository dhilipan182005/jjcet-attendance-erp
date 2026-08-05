package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "email", length = 100)
    private String email;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "lockout_time")
    private LocalDateTime lockoutTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fullName != null) fullName = fullName.trim().toUpperCase();
        if (userId != null) userId = userId.trim().toUpperCase();
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (fullName != null) fullName = fullName.trim().toUpperCase();
        if (userId != null) userId = userId.trim().toUpperCase();
    }
}
