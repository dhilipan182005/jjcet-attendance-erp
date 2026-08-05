package com.example.attendancesystem.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnalyticsResponse {
    private String sessionName;
    private long presentCount;
    private long absentCount;
    private long leaveCount;
    private double attendancePercentage;
}
