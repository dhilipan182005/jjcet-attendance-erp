package com.example.attendancesystem.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchAnalyticsResponse {
    private String name;
    private long totalStudents;
    private long presentCount;
    private long absentCount;
    private long leaveCount;
    private double presentPercentage;
    private double absentPercentage;
    private double leavePercentage;
}
