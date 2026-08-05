package com.example.attendancesystem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Index;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "students",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_register", columnNames = "register_number")
        },
        indexes = {
                @Index(name = "idx_register_number", columnList = "register_number"),
                @Index(name = "idx_student_dept", columnList = "department_id"),
                @Index(name = "idx_student_batch", columnList = "batch_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Builder.Default
    @Column(name = "hosteller", nullable = false)
    private boolean hosteller = false;

    @NotBlank
    @Column(name = "register_number", nullable = false, unique = true, length = 50)
    private String registerNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @NotNull
    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "section_id")
    private Section section;




    @Builder.Default
    @Column(name = "evening_class_enabled", nullable = false)
    private Boolean eveningClassEnabled = false;

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
        if (studentName != null) studentName = studentName.trim().toUpperCase();
        if (registerNumber != null) registerNumber = registerNumber.trim().toUpperCase();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (studentName != null) studentName = studentName.trim().toUpperCase();
        if (registerNumber != null) registerNumber = registerNumber.trim().toUpperCase();
    }

    public boolean isSameDepartmentAndYear(String dept, Integer yr) {
        return this.department.getName().equalsIgnoreCase(dept) && this.academicYear.equals(yr);
    }

    public String getType() {
        return this.hosteller ? "HOSTEL" : "DAY_SCHOLAR";
    }
}
