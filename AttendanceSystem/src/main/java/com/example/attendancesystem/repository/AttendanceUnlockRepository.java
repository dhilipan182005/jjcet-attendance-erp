package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.AttendanceUnlock;
import com.example.attendancesystem.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AttendanceUnlockRepository extends JpaRepository<AttendanceUnlock, Long> {
    Optional<AttendanceUnlock> findByDateAndSession(LocalDate date, Session session);
}
