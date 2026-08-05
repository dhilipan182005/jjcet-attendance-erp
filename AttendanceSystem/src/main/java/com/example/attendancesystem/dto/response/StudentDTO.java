package com.example.attendancesystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentDTO {
    private Long id;
    private String name;
    private String registerNo;
    private String email;
    private String academicYear;
    private String type;
    private Long departmentId;
    private String departmentName;
    private Long batchId;
    private String batchName;
    private boolean eveningClassEnabled;
}
