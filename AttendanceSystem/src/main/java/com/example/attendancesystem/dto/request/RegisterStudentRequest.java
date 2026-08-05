package com.example.attendancesystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterStudentRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Register number is required")
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    private String registerNumber;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotNull(message = "Year is required")
    @Min(1)
    @Max(5)
    private Integer year;

    @NotNull(message = "Batch ID is required")
    private Long batchId;

    private Long sectionId;

    private boolean hosteller;

    private boolean eveningClassEnabled;

    private boolean active = true;
}
