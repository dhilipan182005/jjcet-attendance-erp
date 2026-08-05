package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "attendance_sessions", indexes = {
    @Index(name = "idx_session_status", columnList = "status"),
    @Index(name = "idx_session_teacher", columnList = "teacher_id")
})
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "session_name")
    private Session sessionName;
    @Column(name = "is_active")
    private boolean active;

    public enum SessionStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
}
