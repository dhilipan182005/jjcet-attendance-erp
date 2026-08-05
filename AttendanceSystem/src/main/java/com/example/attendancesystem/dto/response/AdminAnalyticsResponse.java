package com.example.attendancesystem.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnalyticsResponse {

    private long totalStudents;
    private long totalTeachers;
    private long totalDepartments;
    private long totalBatches;
    private long totalEveningEnabled;
    private long totalEveningDisabled;

    private long todayPresent;
    private long todayAbsent;
    private long todayLeave;

    private double todayAttendancePercentage;
    private double weeklyAttendancePercentage;
    private double totalPresentPct;
    private double totalAbsentPct;
    private double monthlyAttendancePercentage;

    private long totalPresent;
    private long totalAbsent;
    private long totalLeave;
}
