package com.example.attendancesystem.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsResponse {
    private long totalStudents;
    private long totalTeachers;
    private long totalDepartments;
    private long totalBatches;
    private long totalEveningEnabled;
    private long totalEveningDisabled;
}
