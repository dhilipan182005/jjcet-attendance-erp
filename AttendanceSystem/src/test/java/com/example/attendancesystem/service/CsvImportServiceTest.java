package com.example.attendancesystem.service;

import com.example.attendancesystem.entity.Batch;
import com.example.attendancesystem.entity.Department;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.BatchRepository;
import com.example.attendancesystem.repository.DepartmentRepository;
import com.example.attendancesystem.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CsvImportServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private RegistrationNumberService registrationNumberService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void importStudents_missingDepartment_returnsErrorInSummary() {
        String csvContent = "register_number,student_name,department_code,department_name\n" +
                "811323106013,Dhilipan S,106,CSE\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csvContent.getBytes());

        RegistrationNumberService.ParsedRegistrationNumber parsed = new RegistrationNumberService.ParsedRegistrationNumber(
                "811323106013", "8113", "23", 2023, "106", "013", 4);
        
        when(registrationNumberService.parse("811323106013")).thenReturn(parsed);
        when(departmentRepository.findByDepartmentCode("106")).thenReturn(Optional.empty());

        com.example.attendancesystem.dto.response.CsvImportSummary summary = csvImportService.preview(file);
        assertEquals(1, summary.getRowsWithErrors());
        assertTrue(summary.getRows().get(0).getMessage().contains("Department with code 106 does not exist."));
    }

    @Test
    void importStudents_missingBatch_returnsErrorInSummary() {
        String csvContent = "register_number,student_name,department_code,department_name\n" +
                "811323106013,Dhilipan S,106,CSE\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csvContent.getBytes());

        RegistrationNumberService.ParsedRegistrationNumber parsed = new RegistrationNumberService.ParsedRegistrationNumber(
                "811323106013", "8113", "23", 2023, "106", "013", 4);
        
        Department dept = new Department();
        dept.setId(1L);
        dept.setDepartmentCode("106");
        dept.setName("CSE");

        when(registrationNumberService.parse("811323106013")).thenReturn(parsed);
        when(departmentRepository.findByDepartmentCode("106")).thenReturn(Optional.of(dept));
        when(batchRepository.findByDepartmentIdAndStartYearAndEndYear(1L, 2023, 2027)).thenReturn(Optional.empty());

        com.example.attendancesystem.dto.response.CsvImportSummary summary = csvImportService.preview(file);
        assertEquals(1, summary.getRowsWithErrors());
        assertTrue(summary.getRows().get(0).getMessage().contains("Batch does not match the admission year in the registration number, or the batch has not been created."));
    }
}
