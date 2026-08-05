package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EditTeacherRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    @NotBlank(message = "Employee ID is required")
    private String employeeId;



    private boolean active;

    private Long departmentId;

    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*_=+-]).{12,}$", 
             message = "Password must be at least 12 chars with upper, lower, number, and special char")
    private String newPassword;
}
