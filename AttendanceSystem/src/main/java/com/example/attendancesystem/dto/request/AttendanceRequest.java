package com.example.attendancesystem.dto.request;

import com.example.attendancesystem.entity.Session;
import com.example.attendancesystem.entity.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AttendanceRequest {

    @NotNull(message = "Student ID is required")
    @Positive(message = "Invalid student ID")
    private Long studentId;

    @NotNull(message = "Session is required")
    private Session session;

    @NotNull(message = "Status is required")
    private Status status;
}
