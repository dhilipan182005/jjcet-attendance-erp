package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterTeacherRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "User ID/Email is required")
    @Email(message = "User ID must be a valid email address")
    private String userId;

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*_=+-]).{12,}$", 
             message = "Password must be at least 12 chars with upper, lower, number, and special char")
    private String password;

    @NotBlank(message = "Employee ID is required")
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    private String employeeId;

    // Was previously accepted by the frontend form but silently ignored by the backend, which
    // hardcoded every new account to TEACHER - meaning there was no way to create a new Admin
    // account at all outside the bootstrap process. Now actually enforced (must be ADMIN or
    // TEACHER - see AdminService.createTeacher).
    @NotBlank(message = "Access is required")
    @Pattern(regexp = "^(ADMIN|TEACHER)$", message = "Access must be ADMIN or TEACHER")
    private String accessType;

    // Optional at creation time - a teacher with no department assigned yet is scoped to
    // see nothing (see TeacherController) rather than everything, until an Admin assigns one.
    private Long departmentId;

}
