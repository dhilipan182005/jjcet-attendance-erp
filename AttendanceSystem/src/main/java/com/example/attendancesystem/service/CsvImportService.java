package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.response.CsvImportSummary;
import com.example.attendancesystem.dto.response.CsvRowResult;
import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Student CSV import: Preview classifies every row without writing anything to the database.
 * Confirm re-reads and re-validates the same file server-side (never trusts a client-supplied
 * preview payload) and applies changes transactionally - if anything critical fails, the whole
 * import rolls back rather than leaving partial data.
 *
 * Expected CSV header: register_number,student_name,department_code,department_name
 * An optional batch_name column is accepted as a consistency check only (see
 * RegistrationNumberService.validateBatchConsistency) - it is never blindly trusted to move a
 * student to a different batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final RegistrationNumberService registrationNumberService;
    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final BatchRepository batchRepository;
    private final CsvImportRepository csvImportRepository;
    private final AuditService auditService;

    private static final List<String> REQUIRED_HEADERS = List.of(
            "register_number", "student_name", "department_code", "department_name");

    public CsvImportSummary preview(MultipartFile file) {
        List<CsvRow> rows = parseCsv(file);
        return process(rows, true, null);
    }

    @Transactional
    public CsvImportSummary confirm(MultipartFile file) {
        List<CsvRow> rows = parseCsv(file);
        String fileHash = computeHash(rows);

        CsvImport importRecord = CsvImport.builder()
                .fileHash(fileHash)
                .importedBy(auditService.getCurrentUserEmail())
                .status("STARTED")
                .totalRows(rows.size())
                .build();
        csvImportRepository.save(importRecord);
        auditService.logAction("CSV_IMPORT_STARTED: " + rows.size() + " rows, file hash " + fileHash.substring(0, 12) + "...");
        log.info("CSV import started for {} rows by user {}", rows.size(), importRecord.getImportedBy());

        try {
            CsvImportSummary summary = process(rows, false, importRecord);

            importRecord.setStatus("COMPLETED");
            importRecord.setNewStudents(summary.getNewStudents());
            importRecord.setUpdatedStudents(summary.getStudentsToUpdate());
            importRecord.setUnchangedStudents(summary.getUnchangedStudents());
            importRecord.setNewDepartments(summary.getNewDepartments());
            importRecord.setNewBatches(summary.getNewBatches());
            importRecord.setErrorRows(summary.getRowsWithErrors());
            importRecord.setCompletedAt(LocalDateTime.now());
            csvImportRepository.save(importRecord);

            auditService.logAction("CSV_IMPORT_COMPLETED: " + summary.getNewStudents() + " new, "
                    + summary.getStudentsToUpdate() + " updated, " + summary.getUnchangedStudents() + " unchanged, "
                    + summary.getNewDepartments() + " new departments, " + summary.getNewBatches() + " new batches, "
                    + summary.getRowsWithErrors() + " errors");
            log.info("CSV import completed: {} new students, {} updated, {} unchanged, {} new departments, {} new batches, {} errors",
                    summary.getNewStudents(), summary.getStudentsToUpdate(), summary.getUnchangedStudents(),
                    summary.getNewDepartments(), summary.getNewBatches(), summary.getRowsWithErrors());

            return summary;
        } catch (RuntimeException e) {
            importRecord.setStatus("FAILED");
            importRecord.setCompletedAt(LocalDateTime.now());
            csvImportRepository.save(importRecord);
            auditService.logAction("CSV_IMPORT_FAILED: " + e.getMessage());
            log.error("CSV import failed: {}", e.getMessage());
            throw e;
        }
    }

    public String csvTemplate() {
        return "register_number,student_name,department_code,department_name\n"
                + "811323106013,Dhilipan S,106,Electronics and Communication Engineering\n"
                + "811324205027,Student Two,205,Computer Science and Engineering\n";
    }

    // ------------------------------------------------------------------
    // Core row processing - shared by preview (dryRun=true) and confirm (dryRun=false).
    // ------------------------------------------------------------------

    private CsvImportSummary process(List<CsvRow> rows, boolean dryRun, CsvImport importRecord) {
        List<CsvRowResult> results = new ArrayList<>();
        int newStudents = 0, updated = 0, unchanged = 0, newDepartments = 0, newBatches = 0, errors = 0;

        // Track department/batch codes created earlier in THIS same file so row 50 can see a
        // department created by row 3, even in dry-run preview where nothing is persisted yet.
        Map<String, Department> departmentsSeenThisRun = new HashMap<>();
        Map<String, Batch> batchesSeenThisRun = new HashMap<>();
        Set<String> registrationNumbersSeenThisRun = new HashSet<>();

        for (CsvRow row : rows) {
            try {
                CsvRowResult result = processRow(row, dryRun, departmentsSeenThisRun, batchesSeenThisRun,
                        registrationNumbersSeenThisRun);
                results.add(result);
                switch (result.getStatus()) {
                    case "NEW_STUDENT" -> newStudents++;
                    case "UPDATE_EXISTING_STUDENT" -> updated++;
                    case "UNCHANGED_STUDENT" -> unchanged++;
                    default -> errors++;
                }
                if ("true".equals(row.extra.get("_newDepartment"))) newDepartments++;
                if ("true".equals(row.extra.get("_newBatch"))) newBatches++;
            } catch (CustomException e) {
                errors++;
                results.add(CsvRowResult.builder()
                        .rowNumber(row.rowNumber)
                        .registerNumber(row.registerNumber)
                        .studentName(row.studentName)
                        .departmentCode(row.departmentCode)
                        .departmentName(row.departmentName)
                        .status("ROW_ERROR")
                        .errorCode(e.getErrorCode())
                        .message(e.getMessage())
                        .build());
            }
        }

        return CsvImportSummary.builder()
                .fileHash(importRecord != null ? importRecord.getFileHash() : computeHash(rows))
                .totalRows(rows.size())
                .newStudents(newStudents)
                .studentsToUpdate(updated)
                .unchangedStudents(unchanged)
                .newDepartments(newDepartments)
                .newBatches(newBatches)
                .rowsWithErrors(errors)
                .rows(results)
                .build();
    }

    private CsvRowResult processRow(CsvRow row, boolean dryRun, Map<String, Department> departmentsSeenThisRun,
                                     Map<String, Batch> batchesSeenThisRun, Set<String> registrationNumbersSeenThisRun) {

        if (row.registerNumber == null || row.registerNumber.isBlank()) {
            throw new CustomException("Registration number is required.", HttpStatus.BAD_REQUEST);
        }
        if (row.studentName == null || row.studentName.isBlank()) {
            throw new CustomException("Student name is required.", HttpStatus.BAD_REQUEST);
        }
        if (row.departmentCode == null || row.departmentCode.isBlank()) {
            throw new CustomException("Department code is required.", HttpStatus.BAD_REQUEST);
        }

        RegistrationNumberService.ParsedRegistrationNumber parsed = registrationNumberService.parse(row.registerNumber);

        if (!registrationNumbersSeenThisRun.add(parsed.registrationNumber)) {
            throw new CustomException("Duplicate registration number within this file.", HttpStatus.BAD_REQUEST);
        }

        if (!parsed.departmentCode.equals(row.departmentCode.trim())) {
            throw new CustomException("Registration number does not match the department.", HttpStatus.BAD_REQUEST);
        }

        // Resolve the department.
        Department department = departmentsSeenThisRun.get(parsed.departmentCode);
        boolean isNewDepartment = false;
        if (department == null) {
            department = departmentRepository.findByDepartmentCode(parsed.departmentCode).orElse(null);
        }
        if (department == null) {
            throw new CustomException("Department with code " + parsed.departmentCode + " does not exist.", HttpStatus.BAD_REQUEST);
        } else {
            String normalizedName = row.departmentName == null ? null : row.departmentName.trim();
            if (normalizedName != null && !normalizedName.isEmpty() && !normalizedName.equalsIgnoreCase(department.getName())) {
                throw new CustomException("Department code already belongs to another department.", HttpStatus.BAD_REQUEST);
            }
        }
        if (department.getId() != null) departmentsSeenThisRun.put(parsed.departmentCode, department);

        // Resolve the expected batch.
        String batchKey = parsed.departmentCode + ":" + parsed.expectedBatchStartYear + "-" + parsed.expectedBatchEndYear;
        Batch batch = batchesSeenThisRun.get(batchKey);
        boolean isNewBatch = false;
        if (batch == null && department.getId() != null) {
            batch = batchRepository.findByDepartmentIdAndStartYearAndEndYear(
                    department.getId(), parsed.expectedBatchStartYear, parsed.expectedBatchEndYear).orElse(null);
        }
        if (batch == null) {
            throw new CustomException("Batch does not match the admission year in the registration number, or the batch has not been created.", HttpStatus.BAD_REQUEST);
        }
        if (batch.getId() != null) batchesSeenThisRun.put(batchKey, batch);

        // Optional batch_name column is a consistency check only, never a trusted override.
        if (row.extra.containsKey("batch_name")) {
            String csvBatchName = row.extra.get("batch_name");
            if (csvBatchName != null && !csvBatchName.isBlank() && !csvBatchName.trim().equals(parsed.expectedBatchName)) {
                throw new CustomException("Batch does not match the admission year in the registration number.", HttpStatus.BAD_REQUEST);
            }
        }

        String normalizedStudentName = row.studentName.trim().toUpperCase();
        Optional<Student> existingOpt = studentRepository.findByRegisterNumberIgnoreCase(parsed.registrationNumber);

        String status;
        String message;

        if (existingOpt.isEmpty()) {
            status = "NEW_STUDENT";
            message = "New student will be added.";
            if (!dryRun) {
                Student student = Student.builder()
                        .studentName(normalizedStudentName)
                        .registerNumber(parsed.registrationNumber)
                        .department(department)
                        .academicYear(parsed.admissionYear)
                        .batch(batch)
                        .hosteller(false)
                        .eveningClassEnabled(false)
                        .active(true)
                        .build();
                studentRepository.save(student);
                auditService.logAction("STUDENT_CREATED_BY_CSV: Register " + parsed.registrationNumber);
            }
        } else {
            Student existing = existingOpt.get();

            // Never let a normal CSV import move a student to a conflicting department - the
            // department is encoded in the registration number itself, so this should only ever
            // trip if the existing record's department was set inconsistently before this system
            // existed.
            if (existing.getDepartment() != null && existing.getDepartment().getDepartmentCode() != null
                    && !existing.getDepartment().getDepartmentCode().equals(parsed.departmentCode)) {
                throw new CustomException("Registration number does not match the department.", HttpStatus.BAD_REQUEST);
            }

            boolean nameChanged = !normalizedStudentName.equals(existing.getStudentName());
            if (!nameChanged) {
                status = "UNCHANGED_STUDENT";
                message = "No changes.";
            } else {
                status = "UPDATE_EXISTING_STUDENT";
                message = "Student name will be updated.";
                if (!dryRun) {
                    String oldName = existing.getStudentName();
                    existing.setStudentName(normalizedStudentName);
                    studentRepository.save(existing);
                    auditService.logAction("STUDENT_UPDATED_BY_CSV: Register " + parsed.registrationNumber
                            + ", name '" + oldName + "' -> '" + normalizedStudentName + "'");
                }
            }
        }

        if (isNewDepartment) row.extra.put("_newDepartment", "true");
        if (isNewBatch) row.extra.put("_newBatch", "true");

        return CsvRowResult.builder()
                .rowNumber(row.rowNumber)
                .registerNumber(parsed.registrationNumber)
                .studentName(normalizedStudentName)
                .departmentCode(parsed.departmentCode)
                .departmentName(department.getName())
                .status(status)
                .message(message)
                .build();
    }

    // ------------------------------------------------------------------
    // CSV parsing (no external dependency - handles a simple quoted-field CSV).
    // ------------------------------------------------------------------

    private static class CsvRow {
        int rowNumber;
        String registerNumber;
        String studentName;
        String departmentCode;
        String departmentName;
        Map<String, String> extra = new HashMap<>();
    }

    private List<CsvRow> parseCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Please select a CSV file.", HttpStatus.BAD_REQUEST, "CSV_INVALID");
        }
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                lines.add(splitCsvLine(line));
            }
        } catch (IOException e) {
            throw new CustomException("Could not read the CSV file.", HttpStatus.BAD_REQUEST, "CSV_INVALID");
        }

        if (lines.isEmpty()) {
            throw new CustomException("The CSV file is empty.", HttpStatus.BAD_REQUEST, "CSV_INVALID");
        }

        String[] header = lines.get(0);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            headerIndex.put(header[i].trim().toLowerCase(), i);
        }
        for (String required : REQUIRED_HEADERS) {
            if (!headerIndex.containsKey(required)) {
                throw new CustomException("CSV file is missing the required column: " + required, HttpStatus.BAD_REQUEST, "CSV_INVALID");
            }
        }

        List<CsvRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i);
            CsvRow row = new CsvRow();
            row.rowNumber = i + 1; // 1-indexed, header is row 1
            row.registerNumber = valueAt(cols, headerIndex, "register_number");
            row.studentName = valueAt(cols, headerIndex, "student_name");
            row.departmentCode = valueAt(cols, headerIndex, "department_code");
            row.departmentName = valueAt(cols, headerIndex, "department_name");
            if (headerIndex.containsKey("batch_name")) {
                row.extra.put("batch_name", valueAt(cols, headerIndex, "batch_name"));
            }
            rows.add(row);
        }
        return rows;
    }

    private String valueAt(String[] cols, Map<String, Integer> headerIndex, String key) {
        Integer idx = headerIndex.get(key);
        if (idx == null || idx >= cols.length) return null;
        return cols[idx].trim();
    }

    /** Minimal RFC4180-style line splitter: handles quoted fields containing commas. */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private String computeHash(List<CsvRow> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (CsvRow row : rows) {
                sb.append(row.registerNumber).append('|').append(row.studentName).append('|')
                        .append(row.departmentCode).append('|').append(row.departmentName).append('\n');
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
