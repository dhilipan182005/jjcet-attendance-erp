package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String userEmail;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "action", nullable = false, length = 200)
    private String action;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "device_type", length = 20)
    private String deviceType;
}
