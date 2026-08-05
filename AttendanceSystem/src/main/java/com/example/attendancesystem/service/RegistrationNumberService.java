package com.example.attendancesystem.service;

import com.example.attendancesystem.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Single source of truth for parsing/validating a Student Registration Number.
 *
 * Format: CCCCYYDDDSSS (exactly 12 digits)
 *   CCCC = 4-digit College Code       (positions 1-4)
 *   YY   = 2-digit Admission Year Code (positions 5-6)
 *   DDD  = 3-digit Department Code     (positions 7-9)
 *   SSS  = 3-digit Student Serial Number (positions 10-12)
 *
 * Example: 811323106013 -> college "8113", admission year 2023, department "106", serial "013",
 * expected batch "2023-2027" (with the configured 4-year course duration).
 *
 * Do not duplicate this parsing logic anywhere else (controllers, other services, CSV import,
 * frontend). Everything that needs to interpret a registration number should call this class.
 */
@Service
public class RegistrationNumberService {

    private static final int TOTAL_LENGTH = 12;
    private static final int COLLEGE_CODE_LEN = 4;
    private static final int ADMISSION_YEAR_CODE_LEN = 2;
    private static final int DEPARTMENT_CODE_LEN = 3;
    private static final int SERIAL_LEN = 3;

    // Century policy: a 2-digit admission year code below this cutoff is assumed 2000s,
    // otherwise 1900s. JJCET has no students admitted before 2000, so 50 is a safe cutoff
    // that also rejects implausible far-future codes when combined with resolveAdmissionYear's
    // upper-bound check below.
    private static final int CENTURY_CUTOFF = 50;

    @Value("${institution.college-code}")
    private String configuredCollegeCode;

    @Value("${institution.engineering-course-duration-years}")
    private int courseDurationYears;

    public static class ParsedRegistrationNumber {
        public final String registrationNumber;
        public final String collegeCode;
        public final String admissionYearCode;
        public final int admissionYear;
        public final String departmentCode;
        public final String studentSerialNumber;
        public final int expectedBatchStartYear;
        public final int expectedBatchEndYear;
        public final String expectedBatchName;

        ParsedRegistrationNumber(String registrationNumber, String collegeCode, String admissionYearCode,
                                  int admissionYear, String departmentCode, String studentSerialNumber,
                                  int courseDurationYears) {
            this.registrationNumber = registrationNumber;
            this.collegeCode = collegeCode;
            this.admissionYearCode = admissionYearCode;
            this.admissionYear = admissionYear;
            this.departmentCode = departmentCode;
            this.studentSerialNumber = studentSerialNumber;
            this.expectedBatchStartYear = admissionYear;
            this.expectedBatchEndYear = admissionYear + courseDurationYears;
            this.expectedBatchName = expectedBatchStartYear + "-" + expectedBatchEndYear;
        }
    }

    /** Trims whitespace only. Does not validate. Use before validate()/parse(). */
    public String normalize(String rawValue) {
        return rawValue == null ? null : rawValue.trim();
    }

    /**
     * Validates format only (does not check college code or resolve/validate admission year -
     * see validateCollegeCode() and resolveAdmissionYear()). Throws CustomException with a
     * simple, user-facing message on failure.
     */
    public void validate(String value) {
        if (value == null || value.isEmpty()) {
            throw new CustomException("Registration number is required.", HttpStatus.BAD_REQUEST);
        }
        if (value.contains(" ") || value.contains("\t")) {
            throw new CustomException("Registration number can contain numbers only.", HttpStatus.BAD_REQUEST);
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new CustomException("Registration number can contain numbers only.", HttpStatus.BAD_REQUEST);
        }
        if (value.length() != TOTAL_LENGTH) {
            throw new CustomException("Registration number must contain exactly 12 digits.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Returns true/false instead of throwing - useful for CSV preview classification. */
    public boolean isValid(String value) {
        try {
            validate(normalize(value));
            return true;
        } catch (CustomException e) {
            return false;
        }
    }

    public String parseCollegeCode(String normalizedValue) {
        return normalizedValue.substring(0, COLLEGE_CODE_LEN);
    }

    public String parseAdmissionYearCode(String normalizedValue) {
        return normalizedValue.substring(COLLEGE_CODE_LEN, COLLEGE_CODE_LEN + ADMISSION_YEAR_CODE_LEN);
    }

    public String parseDepartmentCode(String normalizedValue) {
        int start = COLLEGE_CODE_LEN + ADMISSION_YEAR_CODE_LEN;
        return normalizedValue.substring(start, start + DEPARTMENT_CODE_LEN);
    }

    public String parseStudentSerialNumber(String normalizedValue) {
        int start = COLLEGE_CODE_LEN + ADMISSION_YEAR_CODE_LEN + DEPARTMENT_CODE_LEN;
        return normalizedValue.substring(start, start + SERIAL_LEN);
    }

    /** Resolves a 2-digit admission year code (e.g. "23") to a full year (e.g. 2023). */
    public int resolveAdmissionYear(String admissionYearCode) {
        int code = Integer.parseInt(admissionYearCode);
        int fullYear = code < CENTURY_CUTOFF ? 2000 + code : 1900 + code;
        int currentYear = Year.now().getValue();
        if (fullYear > currentYear + 1) {
            throw new CustomException("Registration number has an invalid admission year.", HttpStatus.BAD_REQUEST);
        }
        if (fullYear < 2000) {
            throw new CustomException("Registration number has an invalid admission year.", HttpStatus.BAD_REQUEST);
        }
        return fullYear;
    }

    public void validateCollegeCode(String collegeCode) {
        if (configuredCollegeCode != null && !configuredCollegeCode.equals(collegeCode)) {
            throw new CustomException("Registration number does not belong to this college.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Confirms a department's stored departmentCode matches the code parsed from the
     * registration number. Called wherever a student is being attached to a department.
     */
    public void validateDepartmentConsistency(String parsedDepartmentCode, String departmentDepartmentCode) {
        if (departmentDepartmentCode == null || !departmentDepartmentCode.equals(parsedDepartmentCode)) {
            throw new CustomException("Registration number does not match the department.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Confirms a candidate batch's start/end year matches what's derived from the admission
     * year. Called wherever a student is being attached to a batch (manual creation, CSV import).
     */
    public void validateBatchConsistency(int expectedStartYear, int expectedEndYear,
                                          Integer candidateStartYear, Integer candidateEndYear) {
        if (candidateStartYear == null || candidateEndYear == null) return;
        if (!candidateStartYear.equals(expectedStartYear) || !candidateEndYear.equals(expectedEndYear)) {
            throw new CustomException(
                    "Batch does not match the admission year in the registration number.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public String deriveExpectedBatch(int admissionYear) {
        return admissionYear + "-" + (admissionYear + courseDurationYears);
    }

    /**
     * Normalizes, validates format, validates college code, and returns the fully parsed,
     * structured result including the derived expected batch. Throws CustomException with a
     * simple user-facing message on any failure - this is the one entry point most callers
     * (student creation, CSV import, search/reports) should use.
     */
    public ParsedRegistrationNumber parse(String rawValue) {
        String normalized = normalize(rawValue);
        validate(normalized);

        String collegeCode = parseCollegeCode(normalized);
        validateCollegeCode(collegeCode);

        String admissionYearCode = parseAdmissionYearCode(normalized);
        int admissionYear = resolveAdmissionYear(admissionYearCode);

        String departmentCode = parseDepartmentCode(normalized);
        String serial = parseStudentSerialNumber(normalized);

        return new ParsedRegistrationNumber(normalized, collegeCode, admissionYearCode, admissionYear,
                departmentCode, serial, courseDurationYears);
    }

    /** Same as parse(), but returns null instead of throwing - for CSV preview rows. */
    public ParsedRegistrationNumber tryParse(String rawValue) {
        try {
            return parse(rawValue);
        } catch (CustomException e) {
            return null;
        }
    }
}
