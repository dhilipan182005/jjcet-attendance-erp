package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.request.AttendanceRequest;
import com.example.attendancesystem.dto.request.BarcodeAttendanceRequest;
import com.example.attendancesystem.dto.response.AttendanceResponse;
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
public class TeacherService {

    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final BatchRepository batchRepository;

    public AttendanceResponse markAttendance(AttendanceRequest request) {
        validateTeacherRole();
        return attendanceService.markAttendanceWithTeacher(request);
    }

    public AttendanceResponse markAttendanceByBarcode(BarcodeAttendanceRequest request) {
        validateTeacherRole();
        return attendanceService.markAttendanceByBarcode(request);
    }

    public Map<String, Object> getDashboardStats() {
        Teacher teacher = getLoggedInTeacher();
        Long deptId = null;
        String deptName = "All Departments";
        
        if (teacher.getUser().getRole() != Role.ADMIN) {
            if (teacher.getDepartment() != null) {
                deptId = teacher.getDepartment().getId();
                deptName = teacher.getDepartment().getName();
            } else {
                deptName = "General Department";
            }
        }

        LocalDate today = LocalDate.now();
        long present = 0, absent = 0, leave = 0;

        if (deptId != null) {
            List<Object[]> deptRows = attendanceRepository.countByDepartmentAndStatusForDateRange(today, today, null, null, null);
            for (Object[] row : deptRows) {
                Long d = (Long) row[0];
                if (d.equals(deptId)) {
                    Status s = (Status) row[1];
                    Long count = (Long) row[2];
                    if (s == Status.P || s == Status.OD) present += count;
                    else if (s == Status.A) absent += count;
                    else if (s == Status.A) leave += count;
                }
            }
        } else {
            List<Object[]> rows = attendanceRepository.countByStatusForDate(today, null, null, null, null);
            for (Object[] row : rows) {
                Status s = (Status) row[0];
                Long count = (Long) row[1];
                if (s == Status.P || s == Status.OD) present += count;
                else if (s == Status.A) absent += count;
                else if (s == Status.A) leave += count;
            }
        }

        Map<String, Boolean> sessionStatus = new LinkedHashMap<>();
        for (Session ses : Session.values()) {
            sessionStatus.put(ses.name(), true);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", deptName);
        long totalStudents = deptId != null ? 
                studentRepository.countByActiveTrueAndDepartmentId(deptId) : 
                studentRepository.countByActiveTrue(null, null, null);
        long deptCount = deptId != null ? 1 : departmentRepository.count();
        long batchCount = batchRepository.countByActiveTrue();

        stats.put("totalStudents", totalStudents);
        stats.put("todayPresent", present);
        stats.put("todayAbsent", absent);
        stats.put("todayLeave", leave);
        stats.put("departmentCount", deptCount);
        stats.put("departmentId", deptId);
        stats.put("batchCount", batchCount);
        stats.put("sessionStatus", sessionStatus);
        return stats;
    }

    public List<Map<String, Object>> searchStudents(String query) {
        return getAttendanceGrid(null, null, null, null, query);
    }

    public List<Map<String, Object>> getAttendanceGrid(Long departmentId, Long batchId, Integer year, Session session, String query) {
        Teacher teacher = getLoggedInTeacher();
        Long resolvedDeptId = departmentId;

        if (teacher.getUser().getRole() != Role.ADMIN) {
            if (teacher.getDepartment() == null) {
                throw new CustomException("Teacher is not assigned to a department", HttpStatus.FORBIDDEN);
            }
            resolvedDeptId = teacher.getDepartment().getId();
        }
        final Long enforcedDeptId = resolvedDeptId;

        List<Student> students;
        if (query != null && !query.trim().isEmpty()) {
            students = studentRepository.searchAllByQuery(query.trim());
            if (enforcedDeptId != null) students.removeIf(s -> !s.getDepartment().getId().equals(enforcedDeptId));
            if (batchId != null) students.removeIf(s -> !s.getBatch().getId().equals(batchId));
            if (year != null) students.removeIf(s -> !s.getAcademicYear().equals(year));
            students.removeIf(s -> s.isActive() == false);
        } else {
            students = studentRepository.findByActiveTrue();
            if (enforcedDeptId != null) students.removeIf(s -> !s.getDepartment().getId().equals(enforcedDeptId));
            if (batchId != null) students.removeIf(s -> !s.getBatch().getId().equals(batchId));
            if (year != null) students.removeIf(s -> !s.getAcademicYear().equals(year));
        }

        if (session == Session.EN) {
            students.removeIf(s -> s.getEveningClassEnabled() == null || !s.getEveningClassEnabled());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("name", s.getStudentName());
            row.put("registerNumber", s.getRegisterNumber());
            row.put("year", s.getAcademicYear());
            row.put("batch", s.getBatch().getName());
            row.put("department", s.getDepartment().getName());
            row.put("departmentName", s.getDepartment().getName());
            row.put("type", s.getType());
            row.put("eveningClassEnabled", s.getEveningClassEnabled() != null ? s.getEveningClassEnabled() : false);
            result.add(row);
        }
        return result;
    }

    private Teacher getLoggedInTeacher() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserIdIgnoreCase(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));
    }

    private void validateTeacherRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_TEACHER") || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}