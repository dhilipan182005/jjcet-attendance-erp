package com.example.attendancesystem.dto.response;

import com.example.attendancesystem.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminResponse {
    private Long id;
    private String name;
    private String userId;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;
}
