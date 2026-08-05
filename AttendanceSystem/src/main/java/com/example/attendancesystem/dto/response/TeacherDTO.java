package com.example.attendancesystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeacherDTO {
    private Long id;
    private String name;
    private String employeeId;
    private String email;

    private boolean eveningClassAccess;
    private boolean active;
    private String departmentName;
    private String createdAt;
    private String updatedAt;
    private String lastLogin;
    private String accessType;
}
