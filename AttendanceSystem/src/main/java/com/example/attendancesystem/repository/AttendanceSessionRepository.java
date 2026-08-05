package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findBySessionId(String sessionId);
    List<AttendanceSession> findByTeacherId(Long teacherId);
    List<AttendanceSession> findByStatus(AttendanceSession.SessionStatus status);
}
