package com.example.attendancesystem.service;

import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttendanceServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AttendanceUnlockRepository attendanceUnlockRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AttendanceService attendanceService;

    private Student mockStudent;
    private Teacher mockTeacher;
    private Department mockDepartment;
    private Batch mockBatch;

    @BeforeEach
    void setUp() {
        mockDepartment = new Department();
        mockDepartment.setId(1L);
        mockDepartment.setName("CSE");

        mockBatch = new Batch();
        mockBatch.setId(1L);
        mockBatch.setName("2023-2027");
        mockBatch.setDepartment(mockDepartment);

        mockStudent = new Student();
        mockStudent.setId(1L);
        mockStudent.setDepartment(mockDepartment);
        mockStudent.setBatch(mockBatch);

        mockTeacher = new Teacher();
        mockTeacher.setId(1L);
    }

    @Test
    void buildAttendance_shouldPopulateSnapshotFields() throws Exception {
        Method buildAttendanceMethod = AttendanceService.class.getDeclaredMethod("buildAttendance", Student.class, Teacher.class, Session.class, Status.class);
        buildAttendanceMethod.setAccessible(true);

        Attendance attendance = (Attendance) buildAttendanceMethod.invoke(attendanceService, mockStudent, mockTeacher, Session.FN, Status.P);

        assertNotNull(attendance);
        assertEquals(mockStudent, attendance.getStudent());
        assertEquals(mockTeacher, attendance.getMarkedBy());
        assertEquals(mockDepartment, attendance.getDepartment(), "Snapshot department field must be populated");
        assertEquals(mockBatch, attendance.getBatch(), "Snapshot batch field must be populated");
        assertEquals(Session.FN, attendance.getSession());
        assertEquals(Status.P, attendance.getStatus());
        assertEquals(LocalDate.now(), attendance.getDate());
    }

    @Test
    void validateTeacherDepartmentScope_wrongDepartment_throwsException() throws Exception {
        Method validateScope = AttendanceService.class.getDeclaredMethod("validateTeacherDepartmentScope", Teacher.class, Student.class);
        validateScope.setAccessible(true);

        Department otherDept = new Department();
        otherDept.setId(99L);
        otherDept.setName("OTHER");

        mockTeacher.setDepartment(otherDept);
        User user = new User();
        user.setRole(Role.TEACHER);
        mockTeacher.setUser(user);

        java.lang.reflect.InvocationTargetException ex = org.junit.jupiter.api.Assertions.assertThrows(
            java.lang.reflect.InvocationTargetException.class, 
            () -> validateScope.invoke(attendanceService, mockTeacher, mockStudent)
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.getCause() instanceof com.example.attendancesystem.exception.CustomException);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getCause().getMessage().contains("Access denied"));
    }
}
