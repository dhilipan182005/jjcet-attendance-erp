package com.example.attendancesystem.service;

import com.example.attendancesystem.entity.Department;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void addDepartment_success() {
        when(departmentRepository.existsByNameIgnoreCase("CSE")).thenReturn(false);
        when(departmentRepository.existsByDepartmentCodeIgnoreCase("104")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(1L);
            return d;
        });

        Department dept = adminService.addDepartment("CSE", "104");

        assertNotNull(dept);
        assertEquals("CSE", dept.getName());
        assertEquals("104", dept.getDepartmentCode());
        verify(departmentRepository).save(any(Department.class));
        verify(auditService).logAction("Department Added: CSE (104)");
    }

    @Test
    void addDepartment_nullOrBlankCode_throwsException() {
        assertThrows(CustomException.class, () -> adminService.addDepartment("CSE", null));
        assertThrows(CustomException.class, () -> adminService.addDepartment("CSE", "   "));
    }

    @Test
    void addDepartment_duplicateCode_throwsException() {
        when(departmentRepository.existsByNameIgnoreCase("CSE")).thenReturn(false);
        when(departmentRepository.existsByDepartmentCodeIgnoreCase("104")).thenReturn(true);

        assertThrows(CustomException.class, () -> adminService.addDepartment("CSE", "104"));
    }
}
