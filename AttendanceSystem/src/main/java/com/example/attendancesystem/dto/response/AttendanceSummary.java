package com.example.attendancesystem.dto.response;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class AttendanceSummary {

    private final long present;
    private final long absent;
    private final long leave;
    private final long total;
    private final double percentage;

    private AttendanceSummary(long present, long absent, long leave) {
        if (present < 0 || absent < 0 || leave < 0) {
            throw new IllegalArgumentException("Attendance values cannot be negative");
        }
        this.present = present;
        this.absent = absent;
        this.leave = leave;
        this.total = present + absent + leave;
        this.percentage = calculatePercentage();
    }

    public static AttendanceSummary of(long present, long absent, long leave) {
        return new AttendanceSummary(present, absent, leave);
    }

    private double calculatePercentage() {
        long effectiveTotal = present + absent;
        if (effectiveTotal == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf((double) present / effectiveTotal * 100)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
