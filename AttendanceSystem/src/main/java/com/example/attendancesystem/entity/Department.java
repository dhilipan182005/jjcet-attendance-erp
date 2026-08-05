package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_name", nullable = false, unique = true, length = 50)
    private String name;

    // 3-digit code parsed from digits 7-9 of a student registration number (see
    // RegistrationNumberService). Nullable for now since existing departments predate this field -
    // an Admin can backfill it from the Departments screen. Enforced unique only when present
    // (see V2 migration's partial unique index).
    @Column(name = "department_code", length = 10)
    private String departmentCode;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = false;
}
