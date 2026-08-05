package com.example.attendancesystem.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    public ApiResponse(boolean success, String message, T data, LocalDateTime timestamp) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, LocalDateTime.now());
    }

    public ApiResponse(boolean success, String message) {
        this(success, message, null, LocalDateTime.now());
    }
}
