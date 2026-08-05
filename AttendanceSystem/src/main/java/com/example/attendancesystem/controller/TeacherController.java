package com.example.attendancesystem.controller;

import com.example.attendancesystem.dto.response.ApiResponse;
import com.example.attendancesystem.dto.response.AttendanceResponse;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.*;
import com.example.attendancesystem.service.AttendanceService;
import com.example.attendancesystem.service.TeacherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final AttendanceService attendanceService;
    private final TeacherService teacherService;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getDepartmentAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Session session) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        Teacher teacher = getLoggedInTeacher();
        Long departmentId = teacher.getDepartment() != null ? teacher.getDepartment().getId() : null;

        if (departmentId == null) {
            log.warn("Teacher {} has no department assigned yet - returning no attendance rows instead of every department's data.", teacher.getEmployeeId());
            return ResponseEntity.ok(ApiResponse.<List<AttendanceResponse>>builder().success(true)
                    .message("No department is assigned to your account yet. Ask an Admin to assign one.")
                    .data(List.of())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        log.info("Teacher {} fetching attendance logs for department: {}, date: {}, session: {}", teacher.getEmployeeId(), departmentId, targetDate, session);
        List<Attendance> logs = attendanceRepository.findDailyReportData(targetDate, session, departmentId, null);
        List<AttendanceResponse> responseList = logs.stream()
                .map(a -> AttendanceResponse.builder()
                        .id(a.getId())
                        .studentId(a.getStudent().getId())
                        .registerNumber(a.getStudent().getRegisterNumber())
                        .studentName(a.getStudent().getStudentName())
                        .date(a.getDate())
                        .session(a.getSession())
                        .status(a.getStatus())
                        .markedBy(a.getMarkedBy().getUser().getFullName())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<AttendanceResponse>>builder().success(true)
                .message("Department attendance logs fetched")
                .data(responseList)
                .timestamp(LocalDateTime.now())
                .build());
      }

    @PutMapping("/attendance/{id}")
    public ResponseEntity<ApiResponse<AttendanceResponse>> updateAttendance(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Status status = Status.valueOf(body.get("status").toUpperCase());
        AttendanceResponse resp = attendanceService.updateAttendance(id, status);
        return ResponseEntity.ok(ApiResponse.<AttendanceResponse>builder().success(true)
                .message("Attendance updated successfully")
                .data(resp)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/attendance/bulk")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> bulkSaveAttendance(
            @RequestBody List<com.example.attendancesystem.dto.request.AttendanceRequest> requests) {
        List<AttendanceResponse> responses = attendanceService.bulkSaveAttendance(requests);
        return ResponseEntity.ok(ApiResponse.<List<AttendanceResponse>>builder().success(true)
                .message("Attendance saved successfully")
                .data(responses)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/attendance/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Attendance deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats() {
        log.info("Teacher fetching dashboard stats");
        Map<String, Object> stats = teacherService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true)
                .message("Dashboard stats fetched")
                .data(stats)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/students/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchStudents(
            @RequestParam(required = false, defaultValue = "") String query) {
        log.info("Teacher searching students with query: {}", query);
        List<Map<String, Object>> results = teacherService.searchStudents(query);
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .message("Student search results")
                .data(results)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/students/grid")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAttendanceGrid(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Session session,
            @RequestParam(required = false) String query) {
        Teacher teacher = getLoggedInTeacher();
        Long departmentId = teacher.getDepartment() != null ? teacher.getDepartment().getId() : null;

        if (departmentId == null) {
            log.warn("Teacher {} has no department assigned yet - returning no grid rows instead of every department's data.", teacher.getEmployeeId());
            return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                    .message("No department is assigned to your account yet. Ask an Admin to assign one.")
                    .data(List.of())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        List<Map<String, Object>> results = teacherService.getAttendanceGrid(departmentId, batchId, year, session, query);
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .message("Attendance grid data")
                .data(results)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/session/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentSession() {
        java.time.LocalDate today = java.time.LocalDate.now();
        String fnStatus = attendanceService.isSessionActive(today, Session.FN) ? "ACTIVE" : "CLOSED";
        String anStatus = attendanceService.isSessionActive(today, Session.AN) ? "ACTIVE" : "CLOSED";
        String enStatus = attendanceService.isSessionActive(today, Session.EN) ? "ACTIVE" : "CLOSED";

        Session current = attendanceService.detectActiveSession();
        String currentSession = current != null ? current.name() : null;

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true)
                .message("Current session status")
                .data(Map.of(
                    "session", currentSession != null ? currentSession : "CLOSED",
                    "status", currentSession != null ? "ACTIVE" : "CLOSED",
                    "sessions", Map.of("FN", fnStatus, "AN", anStatus, "EN", enStatus)
                ))
                .timestamp(LocalDateTime.now())
                .build());
    }

    private Teacher getLoggedInTeacher() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserIdIgnoreCase(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));
    }
}
