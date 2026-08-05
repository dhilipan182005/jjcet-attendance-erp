package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    long countByActiveTrue();

    Optional<Department> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Department> findByActiveTrue();
    Optional<Department> findByDepartmentCode(String departmentCode);
    boolean existsByDepartmentCodeIgnoreCase(String departmentCode);
}
