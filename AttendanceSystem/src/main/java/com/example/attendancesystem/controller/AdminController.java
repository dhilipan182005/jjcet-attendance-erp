package com.example.attendancesystem.controller;

import com.example.attendancesystem.dto.request.RegisterStudentRequest;
import com.example.attendancesystem.dto.request.RegisterTeacherRequest;
import com.example.attendancesystem.dto.response.*;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.repository.AttendanceRepository;
import com.example.attendancesystem.repository.StudentRepository;
import com.example.attendancesystem.service.AdminService;
import com.example.attendancesystem.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    @PostMapping("/roles/assign")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignRole(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        Role role = Role.valueOf(body.get("role"));
        adminService.assignRole(userId, role);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Role assigned successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/students")
    public ResponseEntity<ApiResponse<Void>> createStudent(
            @Valid @RequestBody RegisterStudentRequest request) {

        log.info("Admin requesting to create student: {}", request.getRegisterNumber());
        adminService.createStudent(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<Void>builder().success(true)
                        .message("Student created successfully")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PostMapping("/teachers")
    public ResponseEntity<ApiResponse<Void>> createTeacher(
            @Valid @RequestBody RegisterTeacherRequest request) {

        log.info("Admin requesting to create teacher: {}", request.getUserId());
        adminService.createTeacher(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<Void>builder().success(true)
                        .message("Teacher created successfully")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<ApiResponse<Void>> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody com.example.attendancesystem.dto.request.EditStudentRequest request) {

        log.info("Admin requesting to update student ID: {}", id);
        adminService.updateStudent(id, request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true)
                        .message("Student updated successfully")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @PutMapping("/teachers/{id}")
    public ResponseEntity<ApiResponse<Void>> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody com.example.attendancesystem.dto.request.EditTeacherRequest request) {

        log.info("Admin requesting to update teacher ID: {}", id);
        adminService.updateTeacher(id, request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true)
                        .message("Teacher updated successfully")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<com.example.attendancesystem.dto.response.StudentDTO>>> getStudents(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) Boolean eveningClassEnabled,
            @RequestParam(required = false) String searchQuery) {
        return ResponseEntity.ok(ApiResponse.<List<com.example.attendancesystem.dto.response.StudentDTO>>builder().success(true)
                .message("Students fetched")
                .data(adminService.getStudentsList(departmentId, batchId, academicYear, eveningClassEnabled, searchQuery))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/teachers")
    public ResponseEntity<ApiResponse<List<com.example.attendancesystem.dto.response.TeacherDTO>>> getTeachers(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String searchQuery) {
        return ResponseEntity.ok(ApiResponse.<List<com.example.attendancesystem.dto.response.TeacherDTO>>builder().success(true)
                .message("Teachers fetched")
                .data(adminService.getTeachersList(departmentId, searchQuery))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getAdminStats() {
        log.info("Admin fetching total system stats");
        AdminStatsResponse stats = adminService.getAdminStats(null);
        return ResponseEntity.ok(
                ApiResponse.<AdminStatsResponse>builder().success(true)
                        .message("Stats fetched successfully")
                        .data(stats)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }


    @GetMapping("/analytics/overview")
    public ResponseEntity<?> getOverviewAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Session session) {
        return ResponseEntity.ok(ApiResponse.<AdminAnalyticsResponse>builder().success(true)
                .message("Overview analytics fetched")
                .data(adminService.getAdminAnalytics(startDate, endDate, year, departmentId, batchId, session))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/analytics/department")
    public ResponseEntity<?> getDepartmentAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Session session) {
        List<DepartmentAnalyticsResponse> data = adminService.getDepartmentAnalytics(startDate, endDate, year, batchId, session);
        return ResponseEntity.ok(ApiResponse.<List<DepartmentAnalyticsResponse>>builder().success(true)
                .message("Department analytics fetched")
                .data(data != null ? data : java.util.Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<?> getDailySessionAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Session session) {
        List<SessionAnalyticsResponse> data = adminService.getDailySessionAnalytics(startDate, endDate, year, departmentId, batchId, session);
        return ResponseEntity.ok(ApiResponse.<List<SessionAnalyticsResponse>>builder().success(true)
                .message("Daily session analytics fetched")
                .data(data != null ? data : java.util.Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/analytics/weekly")
    public ResponseEntity<?> getWeeklyTrendAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Session session) {
        List<WeeklyTrendResponse> data = adminService.getWeeklyTrendAnalytics(startDate, endDate, year, departmentId, batchId, session);
        return ResponseEntity.ok(ApiResponse.<List<WeeklyTrendResponse>>builder().success(true)
                .message("Weekly trend analytics fetched")
                .data(data != null ? data : java.util.Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/analytics/monthly")
    public ResponseEntity<?> getMonthlyAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Session session) {
        MonthlyAnalyticsResponse data = adminService.getMonthlyAnalytics(startDate, endDate, year, departmentId, batchId, session);
        return ResponseEntity.ok(ApiResponse.<MonthlyAnalyticsResponse>builder().success(true)
                .message("Monthly analytics fetched")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/analytics/batch")
    public ResponseEntity<?> getBatchAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Session session) {
        List<BatchAnalyticsResponse> data = adminService.getBatchAnalytics(startDate, endDate, year, departmentId, session);
        return ResponseEntity.ok(ApiResponse.<List<BatchAnalyticsResponse>>builder().success(true)
                .message("Batch analytics fetched")
                .data(data != null ? data : java.util.Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build());
    }


    @GetMapping("/departments")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Object>> getDepartments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
            return ResponseEntity.ok(ApiResponse.<Object>builder().success(true)
                    .message("Departments fetched (paginated)")
                    .data(adminService.getAllDepartments(pageable))
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.<Object>builder().success(true)
                .message("Departments fetched")
                .data(adminService.getAllDepartments())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<Department>> addDepartment(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Department>builder().success(true)
                .message("Department created")
                .data(adminService.addDepartment(body.get("name"), body.get("departmentCode")))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/departments/{id}/rename")
    public ResponseEntity<ApiResponse<Department>> updateDepartment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.<Department>builder().success(true)
                .message("Department updated")
                .data(adminService.updateDepartment(id, body.get("name"), body.get("departmentCode")))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/departments/{id}/toggle")
    public ResponseEntity<ApiResponse<Department>> toggleDepartment(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean active = body.containsKey("enabled") ? !body.get("enabled") : body.getOrDefault("active", false);
        return ResponseEntity.ok(ApiResponse.<Department>builder().success(true)
                .message("Department status updated")
                .data(adminService.activeepartment(id, active))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/departments/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        adminService.permanentDeleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Department permanently deleted")
                .timestamp(LocalDateTime.now())
                .build());
    }


    @GetMapping("/batches")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<Object>> getBatches(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
            return ResponseEntity.ok(ApiResponse.<Object>builder().success(true)
                    .message("Batches fetched (paginated)")
                    .data(adminService.getAllBatches(pageable))
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.<Object>builder().success(true)
                .message("Batches fetched")
                .data(adminService.getAllBatches())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<Batch>> addBatch(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Batch>builder().success(true)
                .message("Batch created")
                .data(adminService.addBatch(body.get("name")))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/batches/{id}/rename")
    public ResponseEntity<ApiResponse<Batch>> renameBatch(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.<Batch>builder().success(true)
                .message("Batch renamed")
                .data(adminService.renameBatch(id, body.get("name")))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/batches/{id}/toggle")
    public ResponseEntity<ApiResponse<Batch>> toggleBatch(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean active = body.containsKey("active") ? !body.get("active") : body.getOrDefault("active", false);
        return ResponseEntity.ok(ApiResponse.<Batch>builder().success(true)
                .message("Batch status updated")
                .data(adminService.archiveBatch(id, active))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/batches/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable Long id) {
        adminService.permanentDeleteBatch(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Batch permanently deleted")
                .timestamp(LocalDateTime.now())
                .build());
    }


    @PostMapping("/attendance/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockAttendance(@RequestBody Map<String, Object> body) {
        LocalDate date = LocalDate.parse((String) body.get("date"));
        Session session = Session.valueOf((String) body.get("session"));
        boolean unlocked = (Boolean) body.get("unlocked");
        String reason = (String) body.get("reason");
        Long teacherId = body.get("teacherId") != null && !body.get("teacherId").toString().isEmpty() ? Long.valueOf(body.get("teacherId").toString()) : null;
        Number durationHours = body.get("durationHours") != null && !body.get("durationHours").toString().isEmpty() ? Double.valueOf(body.get("durationHours").toString()) : null;

        adminService.unlockSession(date, session, unlocked, reason, teacherId, durationHours);

        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Attendance lock state updated")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/attendance/lock-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLockStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Session session) {

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true)
                .message("Lock status fetched")
                .data(adminService.getLockStatus(date, session))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Session session,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer academicYear) {
        List<AttendanceResponse> data = adminService.getDailyReport(date, session, departmentId, academicYear);
        return ResponseEntity.ok(ApiResponse.<List<AttendanceResponse>>builder().success(true)
                .message("Daily report fetched")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/reports/monthly")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMonthlyReport(
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer academicYear) {
        List<AttendanceResponse> data = adminService.getMonthlyReport(month, year, departmentId, academicYear);
        return ResponseEntity.ok(ApiResponse.<List<AttendanceResponse>>builder().success(true)
                .message("Monthly report fetched")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/attendance/search")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> searchAttendance(
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String registerNumber,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Session session,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Boolean eveningClassEnabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Searching attendance records, page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending().and(Sort.by("session").ascending()));
        Page<AttendanceResponse> results = adminService.searchAttendance(
                studentName, registerNumber, departmentId, batchId, date, session, status, eveningClassEnabled, pageable
        );

        return ResponseEntity.ok(ApiResponse.<Page<AttendanceResponse>>builder().success(true)
                .message("Search results fetched successfully")
                .data(results)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/attendance/{id}")
    public ResponseEntity<ApiResponse<Void>> updateAttendance(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Status status = Status.valueOf(body.get("status"));
        adminService.updateAttendance(id, status);

        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Attendance updated successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Fetching audit logs, page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> results = adminService.getAuditLogs(pageable);

        return ResponseEntity.ok(ApiResponse.<Page<AuditLog>>builder().success(true)
                .message("Audit logs fetched successfully")
                .data(results)
                .timestamp(LocalDateTime.now())
                .build());
    }


    @DeleteMapping("/students/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(@PathVariable Long id) {
        log.info("Admin requesting to delete student ID: {}", id);
        adminService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Student deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/teachers/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTeacher(@PathVariable Long id) {
        log.info("Admin requesting to delete teacher ID: {}", id);
        adminService.deleteTeacher(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Teacher deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }




    @PutMapping("/students/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreStudent(@PathVariable Long id) {
        log.info("Admin requesting to restore student ID: {}", id);
        adminService.restoreStudent(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Student restored successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/teachers/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreTeacher(@PathVariable Long id) {
        log.info("Admin requesting to restore teacher ID: {}", id);
        adminService.restoreTeacher(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Teacher restored successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/departments/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreDepartment(@PathVariable Long id) {
        log.info("Admin requesting to restore department ID: {}", id);
        adminService.restoreDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Department restored successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PutMapping("/batches/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreBatch(@PathVariable Long id) {
        log.info("Admin requesting to restore batch ID: {}", id);
        adminService.restoreBatch(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true)
                .message("Batch restored successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
