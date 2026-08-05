package com.example.attendancesystem.controller;

import com.example.attendancesystem.dto.request.LoginRequest;
import com.example.attendancesystem.dto.response.ApiResponse;
import com.example.attendancesystem.dto.response.AuthResponse;
import com.example.attendancesystem.service.AuthService;
import com.example.attendancesystem.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) throws Exception {
        log.info("Incoming login request for identifier: {}", request.getIdentifier());
        try {
            AuthResponse data = authService.login(request);
            return ResponseEntity.ok(data);
        } catch(Exception ex) {
            log.warn("Login failed for identifier: {} - reason: {}", request.getIdentifier(), ex.getMessage());
            throw ex;
        }
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<AuthResponse> loginForm(
            @RequestParam(name = "identifier", required = false) String identifierParam,
            @RequestParam(name = "userId", required = false) String userIdParam,
            @RequestParam("password") String password) throws Exception {
        String identifier = (identifierParam != null && !identifierParam.isBlank()) ? identifierParam : userIdParam;
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(password);
        return login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.info("Logging out current user");
        auditService.logAction("Logout Successful");
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().success(true)
                        .message("Logout successful")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
