package com.example.attendancesystem.entity;

public enum Status {

    P(true),
    A(false),
    OD(true);

    private final boolean present;

    Status(boolean present) {
        this.present = present;
    }

    public boolean isPresent() {
        return this == P || this == OD;
    }

    public boolean isAbsent() {
        return this == A;
    }

    public boolean isOnDuty() {
        return this == OD;
    }

    public static Status fromBoolean(boolean present) {
        return present ? P : A;
    }
}
