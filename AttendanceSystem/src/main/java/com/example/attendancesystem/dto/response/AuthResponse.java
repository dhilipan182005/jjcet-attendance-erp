package com.example.attendancesystem.dto.response;

import com.example.attendancesystem.entity.Role;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private boolean success;

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long userId;
    private Long teacherId;

    private String name;

    private String userIdStr;

    private Role role;

    private long expiresIn;
}
