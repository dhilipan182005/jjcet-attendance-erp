package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    long countByActiveTrue();

    Optional<Batch> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Batch> findByActiveTrue();
    Optional<Batch> findByDepartmentIdAndStartYearAndEndYear(Long departmentId, Integer startYear, Integer endYear);
}
