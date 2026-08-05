package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(
        name = "teachers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_teacher_employee", columnNames = "employee_id")
        },
        indexes = {
                @Index(name = "idx_teacher_employee", columnList = "employee_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;


    @Column(name = "employee_id", nullable = false, unique = true, length = 50)
    private String employeeId;

    // Nullable so existing teacher rows keep working until an Admin assigns a department from
    // the Edit Teacher screen. TeacherController's data-scoping queries treat a null department
    // as "no assignment yet" and return an empty result rather than silently returning every
    // department's data (which was the previous behavior when departmentId was hardcoded null).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "access_type", length = 50)
    private String accessType;

    @Column(name = "designation", length = 100)
    private String designation;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;


    @PrePersist
    @PreUpdate
    private void normalize() {
        if (employeeId != null) {
            employeeId = employeeId.trim().toUpperCase();
        }
        if (fullName != null) {
            fullName = fullName.trim().toUpperCase();
        }
        if (accessType != null) {
            accessType = accessType.trim().toUpperCase();
        }
    }
}
