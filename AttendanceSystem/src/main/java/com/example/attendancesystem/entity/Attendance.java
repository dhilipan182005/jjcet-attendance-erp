package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "attendance",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"student_id", "attendance_date", "session"})
        },
        indexes = {
                @Index(name = "idx_student_date", columnList = "student_id, attendance_date"),
                @Index(name = "idx_teacher", columnList = "teacher_id"),
                @Index(name = "idx_attendance_date", columnList = "attendance_date"),
                @Index(name = "idx_attendance_session", columnList = "session")
        }
)
@org.hibernate.annotations.Check(constraints = "session IN ('FN','AN','EN') AND status IN ('P','A','OD')")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher markedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false, updatable = false)
    private LocalDate createdDate;

    @PrePersist
    public void prePersist() {
        this.createdDate = LocalDate.now();
    }
}
