package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Department;
import com.example.attendancesystem.entity.Teacher;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long>,
        JpaSpecificationExecutor<Teacher> {

    long countByActiveTrue();

    Optional<Teacher> findByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Teacher> findByEmailIgnoreCaseAndActiveTrue(String email);

    Optional<Teacher> findByUserId(Long userId);

    Page<Teacher> findAllByOrderByIdDesc(Pageable pageable);
}
