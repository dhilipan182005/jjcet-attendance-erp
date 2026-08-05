package com.example.attendancesystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "csv_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsvImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // STARTED, COMPLETED, FAILED

    @Builder.Default
    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Builder.Default
    @Column(name = "new_students", nullable = false)
    private int newStudents = 0;

    @Builder.Default
    @Column(name = "updated_students", nullable = false)
    private int updatedStudents = 0;

    @Builder.Default
    @Column(name = "unchanged_students", nullable = false)
    private int unchangedStudents = 0;

    @Builder.Default
    @Column(name = "new_departments", nullable = false)
    private int newDepartments = 0;

    @Builder.Default
    @Column(name = "new_batches", nullable = false)
    private int newBatches = 0;

    @Builder.Default
    @Column(name = "error_rows", nullable = false)
    private int errorRows = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
