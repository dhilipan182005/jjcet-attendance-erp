package com.example.attendancesystem.dto.response;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyTrendResponse {
    private LocalDate date;
    private String dayOfWeek;
    private long presentCount;
    private long absentCount;
    private long leaveCount;
    private double attendancePercentage;
}
