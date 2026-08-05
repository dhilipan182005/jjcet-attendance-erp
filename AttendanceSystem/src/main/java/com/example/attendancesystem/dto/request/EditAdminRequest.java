package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EditAdminRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "User ID is required")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Invalid User ID format")
    private String userId;

    private Boolean enabled;
}
