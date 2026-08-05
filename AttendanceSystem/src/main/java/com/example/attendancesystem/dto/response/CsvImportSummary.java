package com.example.attendancesystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportSummary {
    private String fileHash;
    private int totalRows;
    private int newStudents;
    private int studentsToUpdate;
    private int unchangedStudents;
    private int newDepartments;
    private int newBatches;
    private int rowsWithErrors;
    private List<CsvRowResult> rows;
}
