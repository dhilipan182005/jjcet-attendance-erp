package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.response.AttendanceSummary;
import com.example.attendancesystem.dto.response.*;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;

    public Student getCurrentStudent() {
        throw new CustomException("Student portal disabled", HttpStatus.FORBIDDEN);
    }

    public StudentDashboardDetails getStudentDashboardDetails(LocalDate date, Session session,
                                                              Integer month, Integer year) {
        log.info("Fetching student dashboard details - date: {}, session: {}, month: {}, year: {}",
                date, session, month, year);
        Student student = getCurrentStudent();

        long present = attendanceRepository.countByStudentAndStatus(student, Status.P) + 
                       attendanceRepository.countByStudentAndStatus(student, Status.OD);
        long absent = attendanceRepository.countByStudentAndStatus(student, Status.A);
        long leave = attendanceRepository.countByStudentAndStatus(student, Status.A);
        long total = present + absent + leave;

        double currentPct = total == 0 ? 0.0 : Math.round(((double) present / total) * 10000.0) / 100.0;
        double requiredPct = 75.0;
        double shortagePct = currentPct < requiredPct
                ? Math.round((requiredPct - currentPct) * 100.0) / 100.0
                : 0.0;

        List<Object[]> sessionRows = attendanceRepository.countBySessionAndStatusForStudent(student);
        Map<Session, Map<Status, Long>> sessionStats = new HashMap<>();
        for (Object[] row : sessionRows) {
            Session ses = (Session) row[0];
            Status s = (Status) row[1];
            Long count = (Long) row[2];
            sessionStats.computeIfAbsent(ses, k -> new HashMap<>()).put(s, count);
        }

        List<SessionAnalyticsResponse> sessionBreakdown = new ArrayList<>();
        for (Session ses : Session.values()) {
            Map<Status, Long> counts = sessionStats.getOrDefault(ses, Collections.emptyMap());
            long sp = counts.getOrDefault(Status.P, 0L) + counts.getOrDefault(Status.OD, 0L);
            long sa = counts.getOrDefault(Status.A, 0L);
            long sl = counts.getOrDefault(Status.A, 0L);
            long st = sp + sa + sl;
            sessionBreakdown.add(SessionAnalyticsResponse.builder()
                    .sessionName(ses.name())
                    .presentCount(sp)
                    .absentCount(sa)
                    .leaveCount(sl)
                    .attendancePercentage(st == 0 ? 0.0 : Math.round(((double) sp / st) * 10000.0) / 100.0)
                    .build());
        }

        List<Attendance> filtered = attendanceRepository.findFilteredAttendance(student, date, session, month, year);
        List<AttendanceResponse> history = filtered.stream().map(this::mapToResponse).toList();

        return StudentDashboardDetails.builder()
                .currentPercentage(currentPct)
                .requiredPercentage(requiredPct)
                .shortagePercentage(shortagePct)
                .presentCount(present)
                .absentCount(absent)
                .leaveCount(leave)
                .totalCount(total)
                .sessionBreakdown(sessionBreakdown)
                .history(history)
                .build();
    }

    public AttendanceSummary getCurrentStudentSummary() {
        Student student = getCurrentStudent();
        long present = attendanceRepository.countByStudentAndStatus(student, Status.P) + 
                       attendanceRepository.countByStudentAndStatus(student, Status.OD);
        long absent = attendanceRepository.countByStudentAndStatus(student, Status.A);
        long leave = attendanceRepository.countByStudentAndStatus(student, Status.A);
        return AttendanceSummary.of(present, absent, leave);
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        Student student = attendance.getStudent();
        Teacher teacher = attendance.getMarkedBy();
        return AttendanceResponse.builder()
                .studentId(student.getId())
                .registerNumber(student.getRegisterNumber())
                .studentName(student.getStudentName())
                .date(attendance.getDate())
                .session(attendance.getSession())
                .status(attendance.getStatus())
                .markedBy(teacher != null ? teacher.getUser().getFullName() : "System")
                .message(attendance.getStatus() + " marked for " + attendance.getSession())
                .build();
    }
}
