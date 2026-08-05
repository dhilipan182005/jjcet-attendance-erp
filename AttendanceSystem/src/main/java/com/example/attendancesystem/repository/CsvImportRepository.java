package com.example.attendancesystem.repository;

import com.example.attendancesystem.entity.CsvImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CsvImportRepository extends JpaRepository<CsvImport, Long> {
    List<CsvImport> findByFileHashOrderByCreatedAtDesc(String fileHash);
}
