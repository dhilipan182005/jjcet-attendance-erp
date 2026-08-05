package com.example.attendancesystem.service;

import com.example.attendancesystem.entity.Department;
import com.example.attendancesystem.entity.Role;
import com.example.attendancesystem.entity.Teacher;
import com.example.attendancesystem.entity.User;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.BatchRepository;
import com.example.attendancesystem.repository.DepartmentRepository;
import com.example.attendancesystem.repository.StudentRepository;
import com.example.attendancesystem.repository.TeacherRepository;
import com.example.attendancesystem.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeacherServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private com.example.attendancesystem.repository.AttendanceRepository attendanceRepository;

    @InjectMocks
    private TeacherService teacherService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getDashboardStats_noDepartmentAssigned_returnsFallbackStats() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@test.com");
        SecurityContextHolder.setContext(securityContext);

        User user = new User();
        user.setId(1L);
        user.setRole(Role.TEACHER);

        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setUser(user);
        teacher.setDepartment(null);

        when(userRepository.findByUserIdIgnoreCase("test@test.com")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(1L)).thenReturn(Optional.of(teacher));

        Map<String, Object> stats = teacherService.getDashboardStats();
        assertNotNull(stats);
        assertEquals("General Department", stats.get("name"));
        assertNull(stats.get("departmentId"));
    }

    @Test
    void getDashboardStats_assignedDepartment_success() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@test.com");
        SecurityContextHolder.setContext(securityContext);

        User user = new User();
        user.setId(1L);
        user.setRole(Role.TEACHER);

        Department dept = new Department();
        dept.setId(1L);
        dept.setName("CSE");

        Teacher teacher = new Teacher();
        teacher.setId(1L);
        teacher.setUser(user);
        teacher.setDepartment(dept);

        when(userRepository.findByUserIdIgnoreCase("test@test.com")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserId(1L)).thenReturn(Optional.of(teacher));

        when(studentRepository.countByActiveTrueAndDepartmentId(1L)).thenReturn(10L);
        when(batchRepository.countByActiveTrue()).thenReturn(1L);
        
        when(attendanceRepository.countByDepartmentAndStatusForDateRange(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Collections.emptyList());

        java.util.Map<String, Object> stats = teacherService.getDashboardStats();
        
        assertNotNull(stats);
        assertEquals("CSE", stats.get("name"));
        assertEquals(10L, stats.get("totalStudents"));
    }
}
