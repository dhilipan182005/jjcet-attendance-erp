package com.example.attendancesystem.entity;

public enum Session {

    FN("Forenoon"),
    AN("Afternoon"),
    EN("Evening");

    private final String description;

    Session(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
