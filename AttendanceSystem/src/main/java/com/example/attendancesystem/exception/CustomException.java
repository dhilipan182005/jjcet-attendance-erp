package com.example.attendancesystem.exception;

import org.springframework.http.HttpStatus;

public class CustomException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public CustomException(String message, HttpStatus status) {
        this(message, status, getDefaultCodeForStatus(status));
    }

    private static String getDefaultCodeForStatus(HttpStatus status) {
        if (status == HttpStatus.BAD_REQUEST) return "VALIDATION_FAILED";
        if (status == HttpStatus.UNAUTHORIZED) return "AUTHENTICATION_REQUIRED";
        if (status == HttpStatus.FORBIDDEN) return "ACCESS_DENIED";
        if (status == HttpStatus.NOT_FOUND) return "RESOURCE_NOT_FOUND";
        if (status == HttpStatus.CONFLICT) return "CONFLICT";
        if (status == HttpStatus.LOCKED) return "LOCKED";
        return "INTERNAL_ERROR";
    }

    public CustomException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
