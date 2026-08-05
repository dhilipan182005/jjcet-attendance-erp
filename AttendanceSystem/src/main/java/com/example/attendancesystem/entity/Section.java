package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "sections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_section_name_dept", columnNames = {"section_name", "department_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
