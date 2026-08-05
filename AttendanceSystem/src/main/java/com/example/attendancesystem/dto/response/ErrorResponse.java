package com.example.attendancesystem.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    @Builder.Default
    private final String timestamp = LocalDateTime.now().toString();
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final Map<String, String> validationErrors;
    private final String correlationId;
}
