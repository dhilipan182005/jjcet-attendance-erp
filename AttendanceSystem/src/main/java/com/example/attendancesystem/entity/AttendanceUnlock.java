package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "attendance_unlocks",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"date", "session"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Session session;

    @Builder.Default
    @Column(nullable = false)
    private boolean unlocked = true;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_time")
    private java.time.LocalDateTime updatedTime;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "expiry_time")
    private java.time.LocalDateTime expiryTime;
}
