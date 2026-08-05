package com.example.attendancesystem.exception;

import com.example.attendancesystem.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.MDC;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message, jakarta.servlet.http.HttpServletRequest request, java.util.Map<String, String> validationErrors) {
        String path = request != null ? request.getRequestURI() : "unknown";
        String correlationId = MDC.get("correlationId");
        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .correlationId(correlationId)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Business rule violation: {} (status={}, code={})", ex.getMessage(), ex.getStatus().value(), ex.getErrorCode());
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        java.util.Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        
        log.warn("Validation error: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, jakarta.servlet.http.HttpServletRequest request) {
        String detail = ex.getMessage();
        String userMessage = "Request body is malformed or missing required fields.";

        if (detail != null && detail.contains("StudentType")) {
            userMessage = "Invalid Student Type value. Use DAY_SCHOLAR or HOSTEL.";
        } else if (detail != null && detail.contains("not a valid")) {
            userMessage = "Invalid value provided. Please check dropdown selections.";
        } else if (detail != null && detail.contains("Cannot deserialize")) {
            userMessage = "Invalid field value in request. Check all required fields.";
        }

        log.warn("Unreadable HTTP message: {}", detail);
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", userMessage, request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, jakarta.servlet.http.HttpServletRequest request) {
        String cause = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        String userMessage = "A record with the same value already exists.";

        if (cause != null) {
            if (cause.contains("email")) {
                userMessage = "Email address is already registered.";
            } else if (cause.contains("register_number") || cause.contains("registerNumber")) {
                userMessage = "Register number is already registered.";
            } else if (cause.contains("employee_id") || cause.contains("employeeId")) {
                userMessage = "Employee ID is already registered.";
            }
        }

        log.warn("Data integrity violation: {}", cause);
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", userMessage, request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Authorization failed for path: {}", request != null ? request.getRequestURI() : "unknown");
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access this resource", request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getRequestURL());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested endpoint not found.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Unexpected error occurred: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, null);
    }
}
