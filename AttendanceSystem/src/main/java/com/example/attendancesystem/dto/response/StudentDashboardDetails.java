package com.example.attendancesystem.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDashboardDetails {
    private double currentPercentage;
    private double requiredPercentage;
    private double shortagePercentage;
    private long presentCount;
    private long absentCount;
    private long leaveCount;
    private long totalCount;
    private List<SessionAnalyticsResponse> sessionBreakdown;
    private List<AttendanceResponse> history;
}
