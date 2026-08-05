package com.example.attendancesystem.util;

import java.time.LocalTime;

public final class Constants {

    private Constants() {}
    public static final LocalTime MORNING_LIMIT = LocalTime.of(9, 15);
    public static final LocalTime AFTERNOON_LIMIT = LocalTime.of(13, 30);
    public static final LocalTime EVENING_LIMIT = LocalTime.of(18, 0); 

}
