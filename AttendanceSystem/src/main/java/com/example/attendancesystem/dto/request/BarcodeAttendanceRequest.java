package com.example.attendancesystem.dto.request;

import com.example.attendancesystem.entity.Session;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BarcodeAttendanceRequest {

    @NotBlank(message = "Register number is required")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Register number must be alphanumeric")
    @Size(max = 20, message = "Register number must not exceed 20 characters")
    private String registerNumber;

    @NotNull(message = "Session is required")
    private Session session;
}
