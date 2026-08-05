package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.request.RegisterStudentRequest;
import com.example.attendancesystem.dto.request.RegisterTeacherRequest;
import com.example.attendancesystem.dto.response.*;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final BatchRepository batchRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceUnlockRepository attendanceUnlockRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RegistrationNumberService registrationNumberService;

    @Transactional
    public void assignRole(String userId, Role newRole) {
        User user = userRepository.findByUserIdIgnoreCase(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Role oldRole = user.getRole();

        if (oldRole == Role.ADMIN && newRole != Role.ADMIN && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new CustomException("You cannot remove the last active Admin.", HttpStatus.FORBIDDEN, "LAST_ACTIVE_ADMIN_PROTECTED");
        }

        user.setRole(newRole);
        userRepository.save(user);

        teacherRepository.findByUserId(user.getId()).ifPresent(t -> {
            t.setAccessType(newRole.name());
            teacherRepository.save(t);
        });

        auditService.logAction("Access Changed: User " + userId + " changed from " + oldRole + " to " + newRole);
    }


    @Transactional
    public String createStudent(RegisterStudentRequest request) {
        log.info("Creating student with register number: {}", request.getRegisterNumber());
        String registerNumber = normalizeUpper(require(request.getRegisterNumber(), "Register number required"));

        validateStudent(request, registerNumber);
        
        RegistrationNumberService.ParsedRegistrationNumber parsed = registrationNumberService.parse(registerNumber);

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        if (!department.isActive()) {
            throw new CustomException("Department is inactive", HttpStatus.BAD_REQUEST);
        }
        
        if (department.getDepartmentCode() == null || !parsed.departmentCode.equals(department.getDepartmentCode())) {
            throw new CustomException("Registration number does not match the department.", HttpStatus.BAD_REQUEST);
        }

        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        if (!batch.isActive()) {
            throw new CustomException("Batch is inactive", HttpStatus.BAD_REQUEST);
        }
        
        registrationNumberService.validateBatchConsistency(
                parsed.expectedBatchStartYear, parsed.expectedBatchEndYear,
                batch.getStartYear(), batch.getEndYear()
        );



        Student student = Student.builder()
                .studentName(request.getName())
                .registerNumber(registerNumber)
                .department(department)
                .academicYear(request.getYear())
                .eveningClassEnabled(request.isEveningClassEnabled())
                .hosteller(request.isHosteller())
                .active(request.isActive())
                .batch(batch)
                .build();

        studentRepository.save(student);
        auditService.logAction("Student Created: Register " + registerNumber + ", Department " + department.getName());
        return "Student created successfully";
    }

    @Transactional
    public String createTeacher(RegisterTeacherRequest request) {
        log.info("Creating teacher with employee ID: {}", request.getEmployeeId());
        String empId = normalizeUpper(require(request.getEmployeeId(), "Employee ID required"));

        validateTeacher(empId);


        String fullName = normalizeText(request.getName());
        if (fullName != null) fullName = fullName.toUpperCase();

        String email = normalizeUserId(require(request.getUserId(), "Email required"));

        if (teacherRepository.existsByEmailIgnoreCase(email)) {
            throw new CustomException("Email already exists in teachers record", HttpStatus.BAD_REQUEST);
        }

        Role forcedRole = Role.valueOf(request.getAccessType());

        User user = createUserInternal(
                fullName,
                email, 
                request.getPassword(),
                forcedRole
        );
        user.setEmail(email);

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        }

        Teacher teacher = Teacher.builder()
                .user(user)
                .employeeId(empId)
                .email(email)
                .fullName(fullName)
                .department(department)
                .accessType(forcedRole.name())
                .active(true)
                .build();

        teacherRepository.save(teacher);
        auditService.logAction("Teacher Created: Employee ID " + empId);
        return "Teacher created successfully";
    }

    @Transactional
    public void updateStudent(Long id, com.example.attendancesystem.dto.request.EditStudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Student not found", HttpStatus.NOT_FOUND));

        RegistrationNumberService.ParsedRegistrationNumber parsed = registrationNumberService.parse(student.getRegisterNumber());

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        if (!department.isActive()) {
            throw new CustomException("Department is inactive", HttpStatus.BAD_REQUEST);
        }
        
        if (department.getDepartmentCode() == null || !parsed.departmentCode.equals(department.getDepartmentCode())) {
            throw new CustomException("Registration number does not match the department.", HttpStatus.BAD_REQUEST);
        }

        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        if (!batch.isActive()) {
            throw new CustomException("Batch is inactive", HttpStatus.BAD_REQUEST);
        }
        
        registrationNumberService.validateBatchConsistency(
                parsed.expectedBatchStartYear, parsed.expectedBatchEndYear,
                batch.getStartYear(), batch.getEndYear()
        );

        student.setStudentName(normalizeText(request.getName()));
        student.setDepartment(department);
        student.setBatch(batch);
        student.setAcademicYear(request.getYear());
        boolean oldEveningClassEnabled = student.getEveningClassEnabled() != null ? student.getEveningClassEnabled() : false;
        boolean newEveningClassEnabled = request.isEveningClassEnabled();
        student.setEveningClassEnabled(newEveningClassEnabled);
        student.setHosteller(request.isHosteller());
        student.setActive(request.isActive());

        studentRepository.save(student);
        if (oldEveningClassEnabled != newEveningClassEnabled) {
            auditService.logAction(String.format("Student Evening Access Updated\nOld Value: %s\nNew Value: %s", 
                oldEveningClassEnabled ? "YES" : "NO", 
                newEveningClassEnabled ? "YES" : "NO"));
        } else {
            auditService.logAction("Student Edited: Register " + student.getRegisterNumber());
        }
    }

    @Transactional
    public void updateTeacher(Long id, com.example.attendancesystem.dto.request.EditTeacherRequest request) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));

        String newFullName = normalizeText(request.getName());
        if (newFullName != null) newFullName = newFullName.toUpperCase();

        String newEmpId = normalizeUpper(require(request.getEmployeeId(), "Employee ID required"));
        if (!teacher.getEmployeeId().equalsIgnoreCase(newEmpId) && teacherRepository.existsByEmployeeIdIgnoreCase(newEmpId)) {
            throw new CustomException("Employee ID already exists", HttpStatus.BAD_REQUEST);
        }

        String newEmail = normalizeUserId(require(request.getEmail(), "Email required"));
        if (!teacher.getEmail().equalsIgnoreCase(newEmail) && teacherRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new CustomException("Email already exists", HttpStatus.BAD_REQUEST);
        }

        teacher.setFullName(newFullName);
        teacher.setEmployeeId(newEmpId);
        teacher.setEmail(newEmail);
        teacher.setActive(request.isActive());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
            teacher.setDepartment(department);
        }

        User user = teacher.getUser();
        
        if (!request.isActive() && user.isActive() && user.getRole() == Role.ADMIN) {
            if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
                throw new CustomException("You cannot deactivate the last active Admin.", HttpStatus.FORBIDDEN, "LAST_ACTIVE_ADMIN_PROTECTED");
            }
        }
        
        user.setFullName(newFullName);
        user.setEmail(newEmail);
        user.setUserId(newEmail);
        user.setActive(request.isActive());

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        }

        teacherRepository.save(teacher);
        userRepository.save(user);
        auditService.logAction("Teacher Edited: Employee ID " + newEmpId);
    }

    public List<com.example.attendancesystem.dto.response.StudentDTO> getStudentsList(Long departmentId, Long batchId, Integer academicYear, Boolean eveningClassEnabled, String searchQuery) {
        org.springframework.data.jpa.domain.Specification<Student> spec = org.springframework.data.jpa.domain.Specification.where((root, query, cb) -> cb.conjunction());
        if (departmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId));
        }
        if (batchId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("batch").get("id"), batchId));
        }
        if (academicYear != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear"), academicYear));
        }

        if (eveningClassEnabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eveningClassEnabled"), eveningClassEnabled));
        }
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = "%" + searchQuery.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> 
                cb.or(
                    cb.like(cb.lower(root.get("studentName")), q),
                    cb.like(cb.lower(root.get("registerNumber")), q)
                )
            );
        }
        return studentRepository.findAll(spec, Sort.by("id").descending()).stream().map(student -> 
            com.example.attendancesystem.dto.response.StudentDTO.builder()
                .id(student.getId())
                .name(student.getStudentName())
                .registerNo(student.getRegisterNumber())
                .email(null)
                .academicYear(student.getAcademicYear() != null ? String.valueOf(student.getAcademicYear()) : null)
                .type(student.isHosteller() ? "HOSTEL" : "DAY_SCHOLAR")
                .departmentId(student.getDepartment() != null ? student.getDepartment().getId() : null)
                .departmentName(student.getDepartment() != null ? student.getDepartment().getName() : null)
                .batchId(student.getBatch() != null ? student.getBatch().getId() : null)
                .batchName(student.getBatch() != null ? student.getBatch().getName() : null)
                .eveningClassEnabled(Boolean.TRUE.equals(student.getEveningClassEnabled()))
                .build()
        ).collect(java.util.stream.Collectors.toList());
    }

    public List<com.example.attendancesystem.dto.response.TeacherDTO> getTeachersList(Long departmentId, String searchQuery) {
        org.springframework.data.jpa.domain.Specification<Teacher> spec = org.springframework.data.jpa.domain.Specification.where((root, query, cb) -> cb.conjunction());
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = "%" + searchQuery.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> 
                cb.or(
                    cb.like(cb.lower(root.get("fullName")), q),
                    cb.like(cb.lower(root.get("employeeId")), q)
                )
            );
        }
        return teacherRepository.findAll(spec, Sort.by("id").descending()).stream().map(teacher -> 
            com.example.attendancesystem.dto.response.TeacherDTO.builder()
                .id(teacher.getId())
                .name(teacher.getFullName())
                .employeeId(teacher.getEmployeeId())
                .email(teacher.getEmail())
                .active(teacher.isActive())
                .accessType(teacher.getAccessType() != null ? teacher.getAccessType() : "TEACHER")
                .departmentName(teacher.getDesignation() != null ? teacher.getDesignation() : "N/A") 
                .createdAt(teacher.getUser() != null && teacher.getUser().getCreatedAt() != null ? teacher.getUser().getCreatedAt().toString() : "N/A")
                .updatedAt(teacher.getUser() != null && teacher.getUser().getUpdatedAt() != null ? teacher.getUser().getUpdatedAt().toString() : "N/A")
                .lastLogin("N/A")
                .build()
        ).collect(java.util.stream.Collectors.toList());
    }


    @Cacheable(value = "departments")
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Page<Department> getAllDepartments(Pageable pageable) {
        return departmentRepository.findAll(pageable);
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public Department addDepartment(String name, String departmentCode) {
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            throw new CustomException("Department name cannot be empty", HttpStatus.BAD_REQUEST);
        }
        
        String trimmedCode = departmentCode != null ? departmentCode.trim() : "";
        if (trimmedCode.isEmpty()) {
            throw new CustomException("Department code cannot be empty", HttpStatus.BAD_REQUEST);
        }
        
        if (departmentRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new CustomException("Department already exists", HttpStatus.BAD_REQUEST);
        }
        if (departmentRepository.existsByDepartmentCodeIgnoreCase(trimmedCode)) {
            throw new CustomException("Department code already exists", HttpStatus.BAD_REQUEST);
        }
        
        Department department = Department.builder().name(trimmedName).departmentCode(trimmedCode).active(true).build();
        Department saved = departmentRepository.save(department);
        auditService.logAction("Department Added: " + trimmedName + " (" + trimmedCode + ")");
        return saved;
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public Department updateDepartment(Long id, String name, String departmentCode) {
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            throw new CustomException("Department name cannot be empty", HttpStatus.BAD_REQUEST);
        }

        String trimmedCode = departmentCode != null ? departmentCode.trim() : "";
        if (trimmedCode.isEmpty()) {
            throw new CustomException("Department code cannot be empty", HttpStatus.BAD_REQUEST);
        }

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));

        if (!department.getName().equalsIgnoreCase(trimmedName) && departmentRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new CustomException("Department with that name already exists", HttpStatus.BAD_REQUEST);
        }
        
        if (!trimmedCode.equalsIgnoreCase(department.getDepartmentCode()) && departmentRepository.existsByDepartmentCodeIgnoreCase(trimmedCode)) {
            throw new CustomException("Department code already exists", HttpStatus.BAD_REQUEST);
        }

        String oldName = department.getName();
        String oldCode = department.getDepartmentCode();
        department.setName(trimmedName);
        department.setDepartmentCode(trimmedCode);
        Department saved = departmentRepository.save(department);
        auditService.logAction("Department Updated: " + oldName + " (" + oldCode + ") -> " + trimmedName + " (" + trimmedCode + ")");
        return saved;
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public Department activeepartment(Long id, boolean active) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        department.setActive(!active);
        Department saved = departmentRepository.save(department);
        auditService.logAction("Department " + (active ? "active" : "Restored") + ": " + department.getName());
        return saved;
    }


    @Cacheable(value = "batches")
    public List<Batch> getAllBatches() {
        return batchRepository.findAll();
    }

    public Page<Batch> getAllBatches(Pageable pageable) {
        return batchRepository.findAll(pageable);
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public Batch addBatch(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new CustomException("Batch name cannot be empty", HttpStatus.BAD_REQUEST);
        }
        if (batchRepository.existsByNameIgnoreCase(trimmed)) {
            throw new CustomException("Batch already exists", HttpStatus.BAD_REQUEST);
        }
        Batch batch = Batch.builder().name(trimmed).active(true).build();
        Batch saved = batchRepository.save(batch);
        auditService.logAction("Batch Created: " + trimmed);
        return saved;
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public Batch renameBatch(Long id, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new CustomException("Batch name cannot be empty", HttpStatus.BAD_REQUEST);
        }
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));

        if (!batch.getName().equalsIgnoreCase(trimmed) && batchRepository.existsByNameIgnoreCase(trimmed)) {
            throw new CustomException("Batch with that name already exists", HttpStatus.BAD_REQUEST);
        }

        String oldName = batch.getName();
        batch.setName(trimmed);
        Batch saved = batchRepository.save(batch);
        auditService.logAction("Batch Renamed: " + oldName + " -> " + trimmed);
        return saved;
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public Batch archiveBatch(Long id, boolean active) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));
        batch.setActive(!active);
        Batch saved = batchRepository.save(batch);
        auditService.logAction("Batch " + (active ? "active" : "Restored") + ": " + batch.getName());
        return saved;
    }


    @Transactional
    public void unlockSession(LocalDate date, Session session, boolean unlocked, String reason) {
        unlockSession(date, session, unlocked, reason, null, null);
    }

    @Transactional
    public void unlockSession(LocalDate date, Session session, boolean unlocked, String reason, Long teacherId, Number durationHours) {
        AttendanceUnlock unlock = attendanceUnlockRepository.findByDateAndSession(date, session)
                .orElseGet(() -> AttendanceUnlock.builder().date(date).session(session).unlocked(true).build());

        if (unlocked && (reason == null || reason.trim().isEmpty())) {
            throw new CustomException("Please give a reason for unlocking this session.", HttpStatus.BAD_REQUEST);
        }

        unlock.setUnlocked(unlocked);
        unlock.setReason(unlocked ? reason.trim() : null);
        unlock.setTeacherId(unlocked ? teacherId : null);
        if (unlocked && durationHours != null && durationHours.doubleValue() > 0) {
            unlock.setExpiryTime(java.time.LocalDateTime.now().plusMinutes((long) (durationHours.doubleValue() * 60)));
        } else {
            unlock.setExpiryTime(null);
        }
        unlock.setUpdatedBy(auditService.getCurrentUserEmail());
        unlock.setUpdatedTime(java.time.LocalDateTime.now());
        attendanceUnlockRepository.save(unlock);
        auditService.logAction("Attendance Session " + (unlocked ? "Unlocked" : "Locked") +
                ": Date " + date + ", Session " + session + (unlocked ? ", Reason: " + reason.trim() : ""));
        log.info("Admin {} {} session {} on {} for reason: {}", unlock.getUpdatedBy(), unlocked ? "unlocked" : "locked", session, date, unlocked ? reason.trim() : "N/A");
    }

    public java.util.Map<String, Object> getLockStatus(LocalDate date, Session session) {
        return attendanceUnlockRepository.findByDateAndSession(date, session)
                .map(unlock -> {
                    boolean isValid = unlock.isUnlocked();
                    if (isValid && unlock.getExpiryTime() != null && java.time.LocalDateTime.now().isAfter(unlock.getExpiryTime())) {
                        isValid = false;
                    }
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("unlocked", isValid);
                    map.put("updatedBy", unlock.getUpdatedBy());
                    map.put("updatedTime", unlock.getUpdatedTime());
                    map.put("reason", unlock.getReason());
                    map.put("teacherId", unlock.getTeacherId());
                    map.put("expiryTime", unlock.getExpiryTime());
                    return map;
                })
                .orElseGet(() -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("unlocked", true);
                    return map;
                });
    }


    public AdminStatsResponse getAdminStats(Integer year) {
        log.info("Calculating total system stats");
        return AdminStatsResponse.builder()
                .totalStudents(studentRepository.countByActiveTrue(year, null, null))
                .totalTeachers(teacherRepository.countByActiveTrue())
                .totalDepartments(departmentRepository.countByActiveTrue())
                .totalBatches(batchRepository.countByActiveTrue())
                .totalEveningEnabled(studentRepository.countEveningEnabled())
                .totalEveningDisabled(studentRepository.countEveningDisabled())
                .build();
    }

    // Not cached: analytics reflect today's attendance and change continuously through the day.
    // Nothing in the codebase evicts this cache when attendance is marked, so caching it would
    // silently freeze dashboard numbers (e.g. "today present") at whatever they were on first load.
    public AdminAnalyticsResponse getAdminAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long departmentId, Long batchId, Session session) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end;
        long totalStudents = studentRepository.countByActiveTrue(year, departmentId, batchId);
        long totalTeachers = teacherRepository.countByActiveTrue();
        long totalDepartments = departmentRepository.countByActiveTrue();
        long totalBatches = batchRepository.countByActiveTrue();

        long todayPresent = 0, todayAbsent = 0, todayLeave = 0;
        List<Object[]> todayStats = attendanceRepository.countByStatusForDateRange(start, end, year, departmentId, batchId, session);
        for (Object[] row : todayStats) {
            Status s = (Status) row[0];
            long count = (Long) row[1];
            if (s == Status.P || s == Status.OD) todayPresent += count;
            else if (s == Status.A) todayAbsent += count;
        }

        if (totalStudents > 0 && (todayPresent + todayAbsent > totalStudents)) {
            if (todayPresent > totalStudents) todayPresent = totalStudents;
            if (todayPresent + todayAbsent > totalStudents) todayAbsent = totalStudents - todayPresent;
        }
        double todayPct = calculatePercentage(todayPresent, todayAbsent);
        long weeklyPresent = 0, weeklyAbsent = 0;
        List<Object[]> weeklyStats = attendanceRepository.countByStatusForDateRange(
                end.minusDays(6), end, year, departmentId, batchId, session);
        for (Object[] row : weeklyStats) {
            Status s = (Status) row[0];
            long count = (Long) row[1];
            if (s == Status.P || s == Status.OD) weeklyPresent += count;
            else if (s == Status.A) weeklyAbsent += count;
        }
        double weeklyPct = calculatePercentage(weeklyPresent, weeklyAbsent);

        long monthlyPresent = 0, monthlyAbsent = 0;
        List<Object[]> monthlyStats = attendanceRepository.countByStatusForDateRange(
                end.withDayOfMonth(1), end, year, departmentId, batchId, session);
        for (Object[] row : monthlyStats) {
            Status s = (Status) row[0];
            long count = (Long) row[1];
            if (s == Status.P || s == Status.OD) monthlyPresent += count;
            else if (s == Status.A) monthlyAbsent += count;
        }
        double monthlyPct = calculatePercentage(monthlyPresent, monthlyAbsent);

        long totalPresent = 0, totalAbsent = 0, totalLeave = 0;
        List<Object[]> historicalStats = attendanceRepository.countAllByStatus(year, departmentId, batchId, session);
        for (Object[] row : historicalStats) {
            Status s = (Status) row[0];
            long count = (Long) row[1];
            if (s == Status.P || s == Status.OD) totalPresent += count;
            else if (s == Status.A) totalAbsent += count;
            else if (s == Status.A) totalLeave += count;
        }

        double totalPresentPct = totalStudents > 0 ? ((double) todayPresent / totalStudents) * 100.0 : 0.0;
        double totalAbsentPct = totalStudents > 0 ? ((double) todayAbsent / totalStudents) * 100.0 : 0.0;
        if (totalPresentPct + totalAbsentPct > 100.0) {
            double excess = (totalPresentPct + totalAbsentPct) - 100.0;
            totalAbsentPct -= excess;
        }

        return AdminAnalyticsResponse.builder()
                .totalStudents(totalStudents)
                .totalTeachers(totalTeachers)
                .totalDepartments(totalDepartments)
                .totalBatches(totalBatches)
                .todayAttendancePercentage(todayPct)
                .weeklyAttendancePercentage(weeklyPct)
                .monthlyAttendancePercentage(monthlyPct)
                .totalPresentPct(totalPresentPct)
                .totalAbsentPct(totalAbsentPct)
                .todayPresent(todayPresent)
                .todayAbsent(todayAbsent)
                .todayLeave(todayLeave)
                .totalPresent(totalPresent)
                .totalAbsent(totalAbsent)
                .totalLeave(totalLeave)
                .totalEveningEnabled(studentRepository.countEveningEnabled())
                .totalEveningDisabled(studentRepository.countEveningDisabled())
                .build();
    }

    public List<DepartmentAnalyticsResponse> getDepartmentAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long batchId, Session session) {
        List<Department> departments = departmentRepository.findAll();

        Map<Long, Long> studentCounts = new HashMap<>();
        List<Object[]> studentRows = studentRepository.countStudentsPerDepartment(year);
        for (Object[] row : studentRows) {
            studentCounts.put((Long) row[0], (Long) row[1]);
        }

        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);
        List<Object[]> attendanceRows = attendanceRepository.countByDepartmentAndStatusForDateRange(start, end, year, batchId, session);

        Map<Long, Map<Status, Long>> deptAttendance = new HashMap<>();
        for (Object[] row : attendanceRows) {
            Long deptId = (Long) row[0];
            Status s = (Status) row[1];
            Long count = (Long) row[2];
            deptAttendance.computeIfAbsent(deptId, k -> new HashMap<>()).put(s, count);
        }

        List<DepartmentAnalyticsResponse> result = new ArrayList<>();
        for (Department dept : departments) {
            long totalStu = studentCounts.getOrDefault(dept.getId(), 0L);
            Map<Status, Long> counts = deptAttendance.getOrDefault(dept.getId(), Collections.emptyMap());

            long present = counts.getOrDefault(Status.P, 0L) + counts.getOrDefault(Status.OD, 0L);
            long absent = counts.getOrDefault(Status.A, 0L);
            long leave = counts.getOrDefault(Status.A, 0L);
            long totalAtt = present + absent + leave;

            result.add(DepartmentAnalyticsResponse.builder()
                    .name(dept.getName())
                    .presentPercentage(calculatePercentage(present, absent))
                    .absentPercentage(calculatePercentage(absent, present))
                    .leavePercentage(calculatePercentage(leave, present + absent))
                    .totalStudents(totalStu)
                    .presentCount(present)
                    .absentCount(absent)
                    .leaveCount(leave)
                    .build());
        }
        return result;
    }

    public List<BatchAnalyticsResponse> getBatchAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long departmentId, Session session) {
        List<Batch> batches = batchRepository.findAll();

        Map<Long, Long> studentCounts = new HashMap<>();
        List<Object[]> studentRows = studentRepository.countStudentsPerBatch(year);
        for (Object[] row : studentRows) {
            studentCounts.put((Long) row[0], (Long) row[1]);
        }

        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);
        List<Object[]> attendanceRows = attendanceRepository.countByBatchAndStatusForDateRange(start, end, year, departmentId, session);

        Map<Long, Map<Status, Long>> batchAttendance = new HashMap<>();
        for (Object[] row : attendanceRows) {
            Long batchId = (Long) row[0];
            Status s = (Status) row[1];
            Long count = (Long) row[2];
            batchAttendance.computeIfAbsent(batchId, k -> new HashMap<>()).put(s, count);
        }

        List<BatchAnalyticsResponse> result = new ArrayList<>();
        for (Batch batch : batches) {
            long totalStu = studentCounts.getOrDefault(batch.getId(), 0L);
            Map<Status, Long> counts = batchAttendance.getOrDefault(batch.getId(), Collections.emptyMap());

            long present = counts.getOrDefault(Status.P, 0L) + counts.getOrDefault(Status.OD, 0L);
            long absent = counts.getOrDefault(Status.A, 0L);
            long leave = counts.getOrDefault(Status.A, 0L);
            long totalAtt = present + absent + leave;

            result.add(BatchAnalyticsResponse.builder()
                    .name(batch.getName())
                    .totalStudents(totalStu)
                    .presentCount(present)
                    .absentCount(absent)
                    .leaveCount(leave)
                    .presentPercentage(calculatePercentage(present, absent))
                    .absentPercentage(calculatePercentage(absent, present))
                    .leavePercentage(calculatePercentage(leave, present + absent))
                    .build());
        }
        return result;
    }

    public List<SessionAnalyticsResponse> getDailySessionAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long departmentId, Long batchId, Session session) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end;
        List<Object[]> rows = attendanceRepository.countBySessionAndStatusForDateRange(start, end, year, departmentId, batchId, session);

        Map<Session, Map<Status, Long>> sessionStats = new HashMap<>();
        for (Object[] row : rows) {
            Session ses = (Session) row[0];
            Status s = (Status) row[1];
            Long count = (Long) row[2];
            sessionStats.computeIfAbsent(ses, k -> new HashMap<>()).put(s, count);
        }

        List<SessionAnalyticsResponse> result = new ArrayList<>();
        for (Session ses : Session.values()) {
            Map<Status, Long> counts = sessionStats.getOrDefault(ses, Collections.emptyMap());
            long present = counts.getOrDefault(Status.P, 0L) + counts.getOrDefault(Status.OD, 0L);
            long absent = counts.getOrDefault(Status.A, 0L);
            long leave = counts.getOrDefault(Status.A, 0L);
            long total = present + absent + leave;

            result.add(SessionAnalyticsResponse.builder()
                    .sessionName(ses.name())
                    .presentCount(present)
                    .absentCount(absent)
                    .leaveCount(leave)
                    .attendancePercentage(calculatePercentage(present, absent))
                    .build());
        }
        return result;
    }

    public List<WeeklyTrendResponse> getWeeklyTrendAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long departmentId, Long batchId, Session session) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(5);

        List<Object[]> rows = attendanceRepository.countByDateAndStatusForDateRange(start, end, year, departmentId, batchId, session);

        Map<LocalDate, Map<Status, Long>> trendMap = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate dt = (LocalDate) row[0];
            Status s = (Status) row[1];
            Long count = (Long) row[2];
            trendMap.computeIfAbsent(dt, k -> new HashMap<>()).put(s, count);
        }

        List<WeeklyTrendResponse> result = new ArrayList<>();
        for (LocalDate dt = start; !dt.isAfter(end); dt = dt.plusDays(1)) {
            Map<Status, Long> counts = trendMap.getOrDefault(dt, Collections.emptyMap());
            long present = counts.getOrDefault(Status.P, 0L) + counts.getOrDefault(Status.OD, 0L);
            long absent = counts.getOrDefault(Status.A, 0L);
            long leave = counts.getOrDefault(Status.A, 0L);
            long total = present + absent + leave;

            result.add(WeeklyTrendResponse.builder()
                    .date(dt)
                    .dayOfWeek(dt.getDayOfWeek().name())
                    .presentCount(present)
                    .absentCount(absent)
                    .leaveCount(leave)
                    .attendancePercentage(calculatePercentage(present, absent))
                    .build());
        }
        return result;
    }

    public MonthlyAnalyticsResponse getMonthlyAnalytics(LocalDate startDate, LocalDate endDate, Integer year, Long departmentId, Long batchId, Session session) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfMonth(1);

        List<Object[]> rows = attendanceRepository.countByStatusForDateRange(start, end, year, departmentId, batchId, session);
        long present = 0, absent = 0, leave = 0;
        for (Object[] row : rows) {
            Status s = (Status) row[0];
            Long count = (Long) row[1];
            if (s == Status.P || s == Status.OD) present += count;
            else if (s == Status.A) absent = count;
            else if (s == Status.A) leave = count;
        }

        long total = present + absent + leave;
        return MonthlyAnalyticsResponse.builder()
                .averageAttendancePercentage(calculatePercentage(present, absent))
                .presentCount(present)
                .absentCount(absent)
                .leaveCount(leave)
                .build();
    }

    public List<AttendanceResponse> getDailyReport(LocalDate date, Session session, Long departmentId, Integer academicYear) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<Attendance> logs = attendanceRepository.findDailyReportData(targetDate, session, departmentId, academicYear);
        return logs.stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
    }

    public List<AttendanceResponse> getMonthlyReport(Integer month, Integer calendarYear, Long departmentId, Integer academicYear) {
        List<Attendance> logs = attendanceRepository.findMonthlyReportData(month, calendarYear, departmentId, academicYear);
        return logs.stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
    }

    private AttendanceResponse mapToResponse(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId())
                .studentId(a.getStudent().getId())
                .registerNumber(a.getStudent().getRegisterNumber())
                .studentName(a.getStudent().getStudentName())
                .date(a.getDate())
                .session(a.getSession())
                .status(a.getStatus())
                .eveningClassEnabled(a.getStudent().getEveningClassEnabled())
                .markedBy(a.getMarkedBy() != null ? a.getMarkedBy().getUser().getFullName() : "System")
                .build();
    }

    public Page<AttendanceResponse> searchAttendance(
            String studentName,
            String registerNumber,
            Long departmentId,
            Long batchId,
            LocalDate date,
            Session session,
            Status status,
            Boolean eveningClassEnabled,
            org.springframework.data.domain.Pageable pageable) {

        Page<Attendance> page = attendanceRepository.findAll(
                (Specification<Attendance>) (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if ((studentName != null && !studentName.isBlank()) || 
                        (registerNumber != null && !registerNumber.isBlank()) || 
                        departmentId != null || batchId != null) {
                        jakarta.persistence.criteria.Join<Object, Object> studentJoin = root.join("student", jakarta.persistence.criteria.JoinType.INNER);
                        if (studentName != null && !studentName.isBlank()) {
                            predicates.add(cb.like(cb.lower(studentJoin.get("studentName")),
                                    "%" + studentName.toLowerCase() + "%"));
                        }
                        if (registerNumber != null && !registerNumber.isBlank()) {
                            predicates.add(cb.like(cb.lower(studentJoin.get("registerNumber")),
                                    "%" + registerNumber.toLowerCase() + "%"));
                        }
                        if (departmentId != null) {
                            predicates.add(cb.equal(studentJoin.get("department").get("id"), departmentId));
                        }
                        if (batchId != null) {
                            predicates.add(cb.equal(studentJoin.get("batch").get("id"), batchId));
                        }
                        if (eveningClassEnabled != null) {
                            predicates.add(cb.equal(studentJoin.get("eveningClassEnabled"), eveningClassEnabled));
                        }
                    } else if (eveningClassEnabled != null) {
                        jakarta.persistence.criteria.Join<Object, Object> studentJoin = root.join("student", jakarta.persistence.criteria.JoinType.INNER);
                        predicates.add(cb.equal(studentJoin.get("eveningClassEnabled"), eveningClassEnabled));
                    }
                    if (date != null) {
                        predicates.add(cb.equal(root.get("date"), date));
                    }
                    if (session != null) {
                        predicates.add(cb.equal(root.get("session"), session));
                    }
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                }, pageable);

        return page.map(this::mapToResponse);
    }

    @Transactional
    public void updateAttendance(Long id, Status status) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Attendance record not found", HttpStatus.NOT_FOUND));
        attendance.setStatus(status);
        String currentUserEmail = auditService.getCurrentUserEmail();
        Teacher teacher = teacherRepository.findByEmailIgnoreCaseAndActiveTrue(currentUserEmail)
                .orElse(null);
        if (teacher != null) {
            attendance.setMarkedBy(teacher);
        }

        attendanceRepository.save(attendance);
        auditService.logAction("Attendance updated for " + attendance.getStudent().getRegisterNumber() + " to " + status.name());
    }

    public Page<AuditLog> getAuditLogs(org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }


    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Student not found", HttpStatus.NOT_FOUND));
        student.setActive(false);
        studentRepository.save(student);
        auditService.logAction("Student Soft Deleted (Deactivated): " + student.getRegisterNumber());
    }

    @Transactional
    public void deleteTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));

        if (teacher.getUser() != null) {
            String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (teacher.getUser().getUserId().equalsIgnoreCase(currentUserId)) {
                throw new CustomException("You cannot delete your own account.", HttpStatus.FORBIDDEN);
            }
            if (teacher.getUser().getRole() == Role.ADMIN && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
                throw new CustomException("You cannot remove the last active Admin.", HttpStatus.FORBIDDEN);
            }
            // Deactivate the login account too - previously only the Teacher record was
            // deactivated, so a "removed" teacher's account could still authenticate.
            teacher.getUser().setActive(false);
            userRepository.save(teacher.getUser());
        }

        teacher.setActive(false);
        teacherRepository.save(teacher);
        auditService.logAction("Teacher Soft Deleted (Deactivated): " + teacher.getEmployeeId());
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public void deleteDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));

        long activeStudents = studentRepository.countByDepartmentIdAndActiveTrue(id);
        long activeTeachers = teacherRepository.countByActiveTrue();

        if (activeStudents > 0) {
            throw new CustomException("Cannot archive department. Active students are assigned to this department. Move or archive them first.", HttpStatus.BAD_REQUEST);
        }

        dept.setActive(false);
        departmentRepository.save(dept);
        auditService.logAction("Department Soft Deleted (Deactivated): " + dept.getName());
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public void deleteBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));

        long activeStudents = studentRepository.countByBatchIdAndActiveTrue(id);

        if (activeStudents > 0) {
            throw new CustomException("Cannot archive batch. Students are currently assigned to this batch. Move or archive them first.", HttpStatus.BAD_REQUEST);
        }

        batch.setActive(false);
        batchRepository.save(batch);
        auditService.logAction("Batch Soft Deleted (Deactivated): " + batch.getName());
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public void permanentDeleteDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));

        long activeStudents = studentRepository.countByDepartment(dept);

        if (activeStudents > 0) {
            throw new CustomException("Cannot permanently delete department. Students are assigned to this department.", HttpStatus.BAD_REQUEST);
        }

        departmentRepository.delete(dept);
        auditService.logAction("Department Permanently Deleted: " + dept.getName());
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public void permanentDeleteBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));

        long activeStudents = studentRepository.countByBatch(batch);

        if (activeStudents > 0) {
            throw new CustomException("Cannot permanently delete batch. Students are assigned to this batch.", HttpStatus.BAD_REQUEST);
        }

        batchRepository.delete(batch);
        auditService.logAction("Batch Permanently Deleted: " + batch.getName());
    }


    @Transactional
    public void restoreStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Student not found", HttpStatus.NOT_FOUND));
        student.setActive(true);
        studentRepository.save(student);
        auditService.logAction("Student Restored (Reactivated): " + student.getRegisterNumber());
    }

    @Transactional
    public void restoreTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new CustomException("Teacher not found", HttpStatus.NOT_FOUND));

        teacher.setActive(true);
        teacherRepository.save(teacher);

        User user = teacher.getUser();
        if (user != null) {
            user.setActive(true);
            userRepository.save(user);
        }

        auditService.logAction("Teacher Restored (Reactivated): " + teacher.getEmployeeId());
    }

    @CacheEvict(value = "departments", allEntries = true)
    @Transactional
    public void restoreDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));

        dept.setActive(true);
        departmentRepository.save(dept);
        auditService.logAction("Department Restored (Reactivated): " + dept.getName());
    }

    @CacheEvict(value = "batches", allEntries = true)
    @Transactional
    public void restoreBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new CustomException("Batch not found", HttpStatus.NOT_FOUND));

        batch.setActive(true);
        batchRepository.save(batch);
        auditService.logAction("Batch Restored (Reactivated): " + batch.getName());
    }


    private AttendanceResponse mapToAttendanceResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .studentId(attendance.getStudent().getId())
                .registerNumber(attendance.getStudent().getRegisterNumber())
                .studentName(attendance.getStudent().getStudentName())
                .date(attendance.getDate())
                .session(attendance.getSession())
                .status(attendance.getStatus())
                .markedBy(attendance.getMarkedBy().getUser().getFullName())
                .message("Record fetched")
                .build();
    }

    private User createUserInternal(String name, String userId, String password, Role role) {
        String normalizedUserId = normalizeUserId(require(userId, "User ID required"));
        if (userRepository.existsByUserIdIgnoreCase(normalizedUserId)) {
            throw new CustomException("User ID already exists", HttpStatus.BAD_REQUEST);
        }
        return userRepository.save(
                User.builder()
                        .fullName(normalizeText(name))
                        .userId(normalizedUserId)
                        .email(normalizedUserId)
                        .password(passwordEncoder.encode(password))
                        .role(role)
                        .active(true)
                        .build()
        );
    }

    private void validateStudent(RegisterStudentRequest request, String registerNumber) {
        if (studentRepository.existsByRegisterNumberIgnoreCase(registerNumber)) {
            throw new CustomException("Register number already exists", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateTeacher(String empId) {
        if (teacherRepository.existsByEmployeeIdIgnoreCase(empId)) {
            throw new CustomException("Employee ID already exists", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeUserId(String value) { return (value == null || value.isBlank()) ? null : value.trim().toLowerCase(); }
    private String normalizeUpper(String value) { return value.trim().toUpperCase(); }
    private String normalizeText(String value) { return value == null ? null : value.trim(); }
    private String normalizeOptional(String value) { return (value == null || value.isBlank()) ? null : value.trim(); }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CustomException(message, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private double calculatePercentage(long part, long otherPart) {
        long effectiveTotal = part + otherPart;
        if (effectiveTotal == 0) return 0.0;
        return Math.round(((double) part / effectiveTotal) * 10000.0) / 100.0;
    }


    @Transactional
    public String openSession(com.example.attendancesystem.entity.Session sessionName) {
        List<com.example.attendancesystem.entity.AttendanceSession> all = attendanceSessionRepository.findAll();
        for (com.example.attendancesystem.entity.AttendanceSession s : all) {
            s.setActive(s.getSessionName() == sessionName);
            attendanceSessionRepository.save(s);
        }
        auditService.logAction("Admin manually OPENED session: " + sessionName);
        return "Session " + sessionName + " opened successfully";
    }

    @Transactional
    public String closeSession() {
        List<com.example.attendancesystem.entity.AttendanceSession> all = attendanceSessionRepository.findAll();
        for (com.example.attendancesystem.entity.AttendanceSession s : all) {
            s.setActive(false);
            attendanceSessionRepository.save(s);
        }
        auditService.logAction("Admin manually CLOSED all active sessions");
        return "Sessions closed successfully";
    }
}
