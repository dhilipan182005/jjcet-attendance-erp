package com.example.attendancesystem.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyAnalyticsResponse {
    private double averageAttendancePercentage;
    private long presentCount;
    private long absentCount;
    private long leaveCount;
}
