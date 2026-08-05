package com.example.attendancesystem.service;

import com.example.attendancesystem.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class RegistrationNumberServiceTest {

    private RegistrationNumberService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationNumberService();
        ReflectionTestUtils.setField(service, "configuredCollegeCode", "8113");
        ReflectionTestUtils.setField(service, "courseDurationYears", 4);
    }

    @Test
    void parse_validRegistrationNumber_returnsParsedResult() {
        String regNumber = "811323106013";
        RegistrationNumberService.ParsedRegistrationNumber result = service.parse(regNumber);

        assertNotNull(result);
        assertEquals("811323106013", result.registrationNumber);
        assertEquals("8113", result.collegeCode);
        assertEquals("23", result.admissionYearCode);
        assertEquals(2023, result.admissionYear);
        assertEquals("106", result.departmentCode);
        assertEquals("013", result.studentSerialNumber);
        assertEquals(2023, result.expectedBatchStartYear);
        assertEquals(2027, result.expectedBatchEndYear);
        assertEquals("2023-2027", result.expectedBatchName);
    }

    @Test
    void parse_invalidLength_throwsException() {
        String regNumber = "81132310601"; // 11 digits
        CustomException exception = assertThrows(CustomException.class, () -> service.parse(regNumber));
        assertEquals("Registration number must contain exactly 12 digits.", exception.getMessage());
    }

    @Test
    void parse_nonNumericInput_throwsException() {
        String regNumber = "8113231A6013";
        CustomException exception = assertThrows(CustomException.class, () -> service.parse(regNumber));
        assertEquals("Registration number can contain numbers only.", exception.getMessage());
    }

    @Test
    void parse_invalidCollegeCode_throwsException() {
        String regNumber = "999923106013";
        CustomException exception = assertThrows(CustomException.class, () -> service.parse(regNumber));
        assertEquals("Registration number does not belong to this college.", exception.getMessage());
    }

    @Test
    void resolveAdmissionYear_extractsCorrectly() {
        assertEquals(2023, service.resolveAdmissionYear("23"));
        assertEquals(2005, service.resolveAdmissionYear("05"));
        // 99 is >= 50, so it maps to 1999, but that is < 2000, which throws an exception.
        assertThrows(CustomException.class, () -> service.resolveAdmissionYear("99"));
    }

    @Test
    void validateDepartmentConsistency_mismatchThrowsException() {
        assertThrows(CustomException.class, () -> service.validateDepartmentConsistency("106", "104"));
        assertDoesNotThrow(() -> service.validateDepartmentConsistency("106", "106"));
    }

    @Test
    void validateBatchConsistency_mismatchThrowsException() {
        assertThrows(CustomException.class, () -> service.validateBatchConsistency(2023, 2027, 2024, 2028));
        assertDoesNotThrow(() -> service.validateBatchConsistency(2023, 2027, 2023, 2027));
    }
}
