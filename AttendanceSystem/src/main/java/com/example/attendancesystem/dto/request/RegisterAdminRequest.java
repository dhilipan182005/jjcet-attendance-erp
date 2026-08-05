package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterAdminRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "User ID is required")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Invalid User ID format")
    private String userId;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
