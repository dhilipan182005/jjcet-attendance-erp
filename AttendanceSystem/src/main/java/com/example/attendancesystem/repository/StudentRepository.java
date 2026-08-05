package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Batch;
import com.example.attendancesystem.entity.Department;
import com.example.attendancesystem.entity.Student;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.repository.query.Param;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>,
        JpaSpecificationExecutor<Student> {

    @Query("SELECT COUNT(s) FROM Student s WHERE s.active = true " +
           "AND (:academicYear IS NULL OR s.academicYear = :academicYear) " +
           "AND (:departmentId IS NULL OR s.department.id = :departmentId) " +
           "AND (:batchId IS NULL OR s.batch.id = :batchId)")
    long countByActiveTrue(@Param("academicYear") Integer academicYear, @Param("departmentId") Long departmentId, @Param("batchId") Long batchId);

    long countByActiveTrueAndDepartmentId(Long departmentId);
    @Query("SELECT COUNT(s) FROM Student s WHERE s.active = true AND s.eveningClassEnabled = true")
    long countEveningEnabled();

    @Query("SELECT COUNT(s) FROM Student s WHERE s.active = true AND (s.eveningClassEnabled = false OR s.eveningClassEnabled IS NULL)")
    long countEveningDisabled();

    Optional<Student> findByRegisterNumberIgnoreCase(String registerNumber);

    boolean existsByRegisterNumberIgnoreCase(String registerNumber);

    Page<Student> findAll(Pageable pageable);

    Page<Student> findByDepartmentAndAcademicYearAndBatch(
            Department department,
            Integer academicYear,
            Batch batch,
            Pageable pageable
    );

    long countByDepartment(Department department);

    long countByDepartmentAndAcademicYear(Department department, Integer academicYear);

    long countByBatch(Batch batch);

    @Query("SELECT s.department.id, COUNT(s) FROM Student s WHERE s.active = true AND (:academicYear IS NULL OR s.academicYear = :academicYear) GROUP BY s.department.id")
    List<Object[]> countStudentsPerDepartment(@Param("academicYear") Integer academicYear);

    @Query("SELECT s.batch.id, COUNT(s) FROM Student s WHERE s.active = true AND (:academicYear IS NULL OR s.academicYear = :academicYear) GROUP BY s.batch.id")
    List<Object[]> countStudentsPerBatch(@Param("academicYear") Integer academicYear);

    List<Student> findByDepartmentId(Long departmentId);

    long countByDepartmentIdAndActiveTrue(Long departmentId);

    long countByBatchIdAndActiveTrue(Long batchId);

    @Query("SELECT s FROM Student s WHERE s.department.id = :deptId AND s.active = true " +
           "AND (:query IS NULL OR LOWER(s.studentName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Student> searchByDepartmentAndQuery(
            @org.springframework.data.repository.query.Param("deptId") Long deptId,
            @org.springframework.data.repository.query.Param("query") String query);

    boolean existsByDepartmentId(Long departmentId);

    boolean existsByDepartmentIdAndActiveTrue(Long departmentId);

    boolean existsByBatchId(Long batchId);

    @EntityGraph(attributePaths = {"department", "batch"})
    List<Student> findByActiveTrue();

    @EntityGraph(attributePaths = {"department", "batch"})
    @Query("SELECT s FROM Student s WHERE s.active = true AND " +
           "(LOWER(s.studentName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Student> searchAllByQuery(@org.springframework.data.repository.query.Param("query") String query);
}
