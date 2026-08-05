package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.response.AttendanceSummary;
import com.example.attendancesystem.dto.request.AttendanceRequest;
import com.example.attendancesystem.dto.request.BarcodeAttendanceRequest;
import com.example.attendancesystem.dto.response.AttendanceResponse;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AttendanceUnlockRepository attendanceUnlockRepository;
    private final AuditService auditService;

    @Transactional
    public AttendanceResponse markAttendanceWithTeacher(AttendanceRequest request) {
        log.info("Teacher marking attendance for student ID: {} for session: {}", request.getStudentId(), request.getSession());
        Student student = getStudent(request.getStudentId());
        Teacher teacher = getLoggedInTeacher();

        validateSessionLock(request.getSession(), student, LocalDate.now());
        checkDuplicate(student, request.getSession());
        validateTeacherDepartmentScope(teacher, student);

        Attendance attendance = buildAttendance(
                student,
                teacher,
                request.getSession(),
                request.getStatus()
        );

        Attendance saved = attendanceRepository.save(attendance);
        auditService.logAction("Attendance Marked: Student Register " + student.getRegisterNumber() + ", Session " + request.getSession() + ", Status " + request.getStatus());
        log.info("Attendance manually marked for student {} in session {} by teacher {}", student.getRegisterNumber(), request.getSession(), teacher.getEmployeeId());
        return mapToResponse(saved);
    }

    @Transactional
    public List<AttendanceResponse> bulkSaveAttendance(List<AttendanceRequest> requests) {
        log.info("Teacher marking bulk attendance for {} records", requests.size());
        Teacher teacher = getLoggedInTeacher();
        LocalDate date = LocalDate.now();

        List<Attendance> toSave = new ArrayList<>();
        for (AttendanceRequest request : requests) {
            Student student = getStudent(request.getStudentId());
            validateSessionLock(request.getSession(), student, date);
            validateTeacherDepartmentScope(teacher, student);
            Optional<Attendance> existing = attendanceRepository.findByStudentAndDateAndSession(student, date, request.getSession());
            if (existing.isPresent()) {
                Attendance att = existing.get();
                att.setStatus(request.getStatus());
                toSave.add(att);
            } else {
                toSave.add(buildAttendance(student, teacher, request.getSession(), request.getStatus()));
            }
        }

        List<Attendance> savedList = attendanceRepository.saveAll(toSave);
        auditService.logAction("Bulk Attendance Marked: " + savedList.size() + " records");
        log.info("Bulk attendance marked for {} students by teacher {}", savedList.size(), teacher.getEmployeeId());
        return savedList.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public AttendanceResponse markAttendanceByBarcode(BarcodeAttendanceRequest request) {
        log.info("Processing barcode attendance for register number: {} in session: {}", request.getRegisterNumber(), request.getSession());
        String regNumber = normalizeUpper(request.getRegisterNumber());

        Student student = studentRepository
                .findByRegisterNumberIgnoreCase(regNumber)
                .orElseThrow(() ->
                        new CustomException("Student not found", HttpStatus.NOT_FOUND));

        Teacher teacher = getLoggedInTeacher();


        Session session = request.getSession();
        if (session == null) {
            session = detectActiveSession();
            if (session == null) {
                throw new CustomException("No active attendance session at this time", HttpStatus.BAD_REQUEST);
            }
        }

        validateSessionLock(session, student, LocalDate.now());
        validateTeacherDepartmentScope(teacher, student);
        checkDuplicate(student, session);

        Attendance attendance = buildAttendance(
                student,
                teacher,
                session,
                Status.P
        );

        Attendance saved = attendanceRepository.save(attendance);
        auditService.logAction("Barcode Attendance Marked: Student Register " + student.getRegisterNumber() + ", Session " + session + ", Status PRESENT");
        log.info("Barcode attendance marked for student {} in session {} by teacher {}", student.getRegisterNumber(), session, teacher.getEmployeeId());
        return mapToResponse(saved);
    }

    @Transactional
    public AttendanceResponse updateAttendance(Long id, Status newStatus) {
        log.info("Updating attendance ID: {} to status: {}", id, newStatus);
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Attendance record not found", HttpStatus.NOT_FOUND));

        Teacher teacher = getLoggedInTeacher();
        Student student = attendance.getStudent();

        validateTeacherDepartmentScope(teacher, student);
        validateSessionLock(attendance.getSession(), student, attendance.getDate());

        Status oldStatus = attendance.getStatus();
        attendance.setStatus(newStatus);
        Attendance saved = attendanceRepository.save(attendance);

        auditService.logAction("Attendance Updated: ID " + id + ", Student Register " + student.getRegisterNumber() + ", Status " + oldStatus + " -> " + newStatus);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteAttendance(Long id) {
        log.info("Deleting attendance ID: {}", id);
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Attendance record not found", HttpStatus.NOT_FOUND));

        Teacher teacher = getLoggedInTeacher();
        Student student = attendance.getStudent();

        validateTeacherDepartmentScope(teacher, student);
        validateSessionLock(attendance.getSession(), student, attendance.getDate());

        attendanceRepository.delete(attendance);
        auditService.logAction("Attendance Deleted: ID " + id + ", Student Register " + student.getRegisterNumber() + ", Session " + attendance.getSession());
    }

    public AttendanceSummary getAttendanceSummary(Long studentId) {
        log.info("Calculating attendance summary for student ID: {}", studentId);
        Student student = getStudent(studentId);

        long present = attendanceRepository.countByStudentAndStatus(student, Status.P);
        long absent = attendanceRepository.countByStudentAndStatus(student, Status.A);
        long onDuty = attendanceRepository.countByStudentAndStatus(student, Status.OD);

        return AttendanceSummary.of(present, absent, onDuty);
    }

    private Student getStudent(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() ->
                        new CustomException("Student not found", HttpStatus.NOT_FOUND));
    }

    private Teacher getLoggedInTeacher() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserIdIgnoreCase(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));
    }

    private Attendance buildAttendance(Student student,
                                       Teacher teacher,
                                       Session session,
                                       Status status) {
        return Attendance.builder()
                .student(student)
                .markedBy(teacher)
                .department(student.getDepartment())
                .batch(student.getBatch())
                .date(LocalDate.now())
                .session(session)
                .status(status)
                .build();
    }

    private void checkDuplicate(Student student, Session session) {
        attendanceRepository
                .findByStudentAndDateAndSession(student, LocalDate.now(), session)
                .ifPresent(a -> {
                    throw new CustomException("Attendance already marked", HttpStatus.BAD_REQUEST);
                });
    }

    private void validateSessionLock(Session session, Student student, LocalDate date) {
        if (session == Session.EN && !Boolean.TRUE.equals(student.getEveningClassEnabled())) {
            throw new CustomException("Student is not assigned to evening classes", HttpStatus.BAD_REQUEST);
        }

        if (!isSessionActive(date, session)) {
            throw new CustomException("Attendance session is locked. Admin only can unlock attendance.", HttpStatus.FORBIDDEN);
        }
    }

    private void validateTeacherDepartmentScope(Teacher teacher, Student student) {
        if (teacher.getUser().getRole() == Role.ADMIN) {
            return; // Admins can mark attendance for anyone
        }
        if (teacher.getDepartment() == null || student.getDepartment() == null || 
            !teacher.getDepartment().getId().equals(student.getDepartment().getId())) {
            throw new CustomException("Access denied: You can only mark attendance for students in your department", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isSessionActiveByTime(Session session) {
        LocalTime now = LocalTime.now();
        switch (session) {
            case FN:
                return !now.isBefore(LocalTime.of(9, 0)) && !now.isAfter(LocalTime.of(10, 30));
            case AN:
                return !now.isBefore(LocalTime.of(13, 0)) && !now.isAfter(LocalTime.of(14, 0));
            case EN:
                return !now.isBefore(LocalTime.of(17, 0)) && !now.isAfter(LocalTime.of(19, 30));
            default:
                return false;
        }
    }

    public boolean isSessionUnlockedByAdmin(LocalDate date, Session session) {
        return attendanceUnlockRepository.findByDateAndSession(date, session)
                .map(unlock -> {
                    if (!unlock.isUnlocked()) return false;
                    if (unlock.getExpiryTime() != null && java.time.LocalDateTime.now().isAfter(unlock.getExpiryTime())) {
                        return false;
                    }
                    return true;
                })
                .orElse(false);
    }

    public boolean isSessionActive(LocalDate date, Session session) {
        boolean isToday = LocalDate.now().equals(date);
        boolean activeByTime = isToday && isSessionActiveByTime(session);
        boolean adminUnlocked = isSessionUnlockedByAdmin(date, session);
        return activeByTime || adminUnlocked;
    }

    public Session detectActiveSession() {
        LocalDate today = LocalDate.now();
        // Prefer whichever session actually matches the real current time first. An admin
        // manually unlocking an earlier session for backfill should not cause auto-detected
        // (no-session-param) barcode scans to be silently routed to that earlier session while
        // a different session's time window is genuinely active right now.
        if (isSessionActiveByTime(Session.FN)) return Session.FN;
        if (isSessionActiveByTime(Session.AN)) return Session.AN;
        if (isSessionActiveByTime(Session.EN)) return Session.EN;
        // No time window is live right now - fall back to any session an admin unlocked today.
        if (isSessionUnlockedByAdmin(today, Session.FN)) return Session.FN;
        if (isSessionUnlockedByAdmin(today, Session.AN)) return Session.AN;
        if (isSessionUnlockedByAdmin(today, Session.EN)) return Session.EN;
        return null;
    }

    private String normalizeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .studentId(attendance.getStudent().getId())
                .registerNumber(attendance.getStudent().getRegisterNumber())
                .studentName(attendance.getStudent().getStudentName())
                .date(attendance.getDate())
                .session(attendance.getSession())
                .status(attendance.getStatus())
                .markedBy(attendance.getMarkedBy().getUser().getFullName())
                .message(attendance.getStatus() + " marked for " + attendance.getSession())
                .build();
    }
}
