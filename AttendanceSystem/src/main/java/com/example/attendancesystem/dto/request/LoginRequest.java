package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Identifier (User ID or Email) is required")
    @Size(max = 100)
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100)
    private String password;
}
