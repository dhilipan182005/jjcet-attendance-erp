package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Attendance;
import com.example.attendancesystem.entity.Session;
import com.example.attendancesystem.entity.Student;
import com.example.attendancesystem.entity.Status;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long>,
        JpaSpecificationExecutor<Attendance> {

    Page<Attendance> findByStudentOrderByDateDesc(Student student, Pageable pageable);

    Page<Attendance> findByDate(LocalDate date, Pageable pageable);

    Optional<Attendance> findByStudentAndDateAndSession(
            Student student,
            LocalDate date,
            Session session
    );

    long countByStudentAndStatus(Student student, Status status);

    long countByStudent(Student student);

    long countByStudentAndDateBetween(
            Student student,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByStudentAndStatusAndDateBetween(
            Student student,
            Status status,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByDate(LocalDate date);

    long countByDateAndStatus(LocalDate date, Status status);


    @Query("SELECT a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE a.date = :date " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "GROUP BY a.status")
    List<Object[]> countByStatusForDate(@Param("date") LocalDate date, @Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId, @Param("session") Session session);

    @Query("SELECT a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE a.date BETWEEN :startDate AND :endDate " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "GROUP BY a.status")
    List<Object[]> countByStatusForDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId, @Param("session") Session session);

    @Query("SELECT COALESCE(a.department.id, s.department.id), a.status, COUNT(DISTINCT a.student) FROM Attendance a JOIN a.student s WHERE a.date BETWEEN :startDate AND :endDate AND s.active = true " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR s.academicYear = :academicYear) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, s.batch.id) = :batchId) " +
           "GROUP BY COALESCE(a.department.id, s.department.id), a.status")
    List<Object[]> countByDepartmentAndStatusForDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("academicYear") Integer academicYear, @Param("batchId") Long batchId, @Param("session") Session session);

    @Query("SELECT COALESCE(a.batch.id, s.batch.id), a.status, COUNT(DISTINCT a.student) FROM Attendance a JOIN a.student s WHERE a.date BETWEEN :startDate AND :endDate AND s.active = true " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR s.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, s.department.id) = :departmentId) " +
           "GROUP BY COALESCE(a.batch.id, s.batch.id), a.status")
    List<Object[]> countByBatchAndStatusForDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("session") Session session);

    @Query("SELECT a.session, a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE a.date BETWEEN :startDate AND :endDate " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "GROUP BY a.session, a.status")
    List<Object[]> countBySessionAndStatusForDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId, @Param("session") Session session);

    @Query("SELECT a.session, a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE a.student = :student GROUP BY a.session, a.status")
    List<Object[]> countBySessionAndStatusForStudent(@Param("student") Student student);

    @Query("SELECT a.date, a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE a.date BETWEEN :startDate AND :endDate " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "GROUP BY a.date, a.status")
    List<Object[]> countByDateAndStatusForDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId, @Param("session") Session session);

    @Query("SELECT a.status, COUNT(DISTINCT a.student) FROM Attendance a WHERE " +
           "(:session IS NULL OR a.session = :session) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "GROUP BY a.status")
    List<Object[]> countAllByStatus(@Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId, @Param("session") Session session);

    @EntityGraph(attributePaths = {"student", "student.department", "student.batch", "markedBy"})
    @Query("SELECT a FROM Attendance a WHERE a.student = :student " +
           "AND (:date IS NULL OR a.date = :date) " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:month IS NULL OR EXTRACT(MONTH FROM a.date) = :month) " +
           "AND (:academicYear IS NULL OR EXTRACT(YEAR FROM a.date) = :academicYear) " +
           "ORDER BY a.date DESC, a.session ASC")
    List<Attendance> findFilteredAttendance(
            @Param("student") Student student,
            @Param("date") LocalDate date,
            @Param("session") Session session,
            @Param("month") Integer month,
            @Param("academicYear") Integer academicYear
    );

    @EntityGraph(attributePaths = {"student", "student.department", "student.batch", "markedBy"})
    @Query("SELECT a FROM Attendance a WHERE " +
           "(:date IS NULL OR a.date = :date) " +
           "AND (:session IS NULL OR a.session = :session) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "ORDER BY a.student.registerNumber ASC, a.session ASC")
    List<Attendance> findDailyReportData(
            @Param("date") LocalDate date,
            @Param("session") Session session,
            @Param("departmentId") Long departmentId,
            @Param("academicYear") Integer academicYear
    );

    @EntityGraph(attributePaths = {"student", "student.department", "student.batch", "markedBy"})
    @Query("SELECT a FROM Attendance a WHERE " +
           "EXTRACT(MONTH FROM a.date) = :month " +
           "AND EXTRACT(YEAR FROM a.date) = :calendarYear " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:academicYear IS NULL OR a.student.academicYear = :academicYear) " +
           "ORDER BY a.student.registerNumber ASC, a.date ASC")
    List<Attendance> findMonthlyReportData(
            @Param("month") Integer month,
            @Param("calendarYear") Integer calendarYear,
            @Param("departmentId") Long departmentId,
            @Param("academicYear") Integer academicYear
    );

    @EntityGraph(attributePaths = {"student", "student.department", "student.batch", "markedBy"})
    @Query("SELECT a FROM Attendance a WHERE " +
           "(:date IS NULL OR a.date = :date) " +
           "AND (:month IS NULL OR EXTRACT(MONTH FROM a.date) = :month) " +
           "AND (:academicYear IS NULL OR EXTRACT(YEAR FROM a.date) = :academicYear) " +
           "AND (:departmentId IS NULL OR COALESCE(a.department.id, a.student.department.id) = :departmentId) " +
           "AND (:batchId IS NULL OR COALESCE(a.batch.id, a.student.batch.id) = :batchId) " +
           "")
    List<Attendance> findReportData(
            @Param("date") LocalDate date,
            @Param("month") Integer month,
            @Param("academicYear") Integer academicYear,
            @Param("departmentId") Long departmentId,
            @Param("batchId") Long batchId
    );
}
