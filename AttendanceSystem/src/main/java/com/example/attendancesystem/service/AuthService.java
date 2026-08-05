package com.example.attendancesystem.service;

import com.example.attendancesystem.dto.request.LoginRequest;
import com.example.attendancesystem.dto.response.AuthResponse;
import com.example.attendancesystem.entity.User;
import com.example.attendancesystem.exception.CustomException;
import com.example.attendancesystem.repository.UserRepository;
import com.example.attendancesystem.repository.TeacherRepository;
import com.example.attendancesystem.entity.Teacher;
import com.example.attendancesystem.entity.Role;
import com.example.attendancesystem.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthResponse login(LoginRequest request) {
        log.info("Authentication request received for identifier: {}", request.getIdentifier());

        String identifier = require(request.getIdentifier(), "Identifier required").trim();
        String password = require(request.getPassword(), "Password required");

        User user = null;
        if (identifier.contains("@")) {
            user = userRepository.findByEmailIgnoreCase(identifier).orElse(null);
        } else {
            user = userRepository.findByUserIdIgnoreCase(identifier).orElse(null);
        }

        if (user == null) {
            log.warn("Invalid credentials for identifier: {}", identifier);
            auditService.logAction("Login Failed: Unknown identifier '" + identifier + "'");
            throw new CustomException("Invalid user ID/email address or password.", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isActive()) {
            throw new CustomException("Account disabled", HttpStatus.FORBIDDEN);
        }

        if (user.getLockoutTime() != null) {
            long lockDuration = Duration.between(user.getLockoutTime(), LocalDateTime.now()).toMinutes();
            if (lockDuration < 15) {
                throw new CustomException("Account locked. Try again in " + (15 - lockDuration) + " minutes", HttpStatus.LOCKED);
            } else {
                user.setFailedLoginAttempts(0);
                user.setLockoutTime(null);
                userRepository.save(user);
            }
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= 5) {
                user.setLockoutTime(LocalDateTime.now());
                userRepository.save(user);
                auditService.logAction("Account Locked: '" + identifier + "' after 5 failed login attempts");
                throw new CustomException("Your account is temporarily locked due to multiple unsuccessful sign-in attempts. Please try again later.", HttpStatus.LOCKED);
            }
            userRepository.save(user);
            log.warn("Invalid credentials for identifier: {}", identifier);
            auditService.logAction("Login Failed: Incorrect password for '" + identifier + "'");
            throw new CustomException("Invalid user ID/email address or password.", HttpStatus.UNAUTHORIZED);
        }

        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockoutTime(null);
            userRepository.save(user);
        }

        Long teacherId = null;
        if (user.getRole() == Role.TEACHER) {
            Teacher teacher = teacherRepository.findByUserId(user.getId()).orElse(null);
            if (teacher != null) {
                teacherId = teacher.getId();
            }
        }

        String token = jwtUtil.generateToken(
                user.getUserId(),
                user.getRole().name(),
                teacherId
        );
        auditService.logAction("Login Successful");

        return AuthResponse.builder()
                .success(true)
                .token(token)
                .userId(user.getId())
                .teacherId(teacherId)
                .name(user.getFullName())
                .userIdStr(user.getUserId())
                .role(user.getRole())
                .expiresIn(jwtExpiration / 1000)
                .build();
    }

    private String normalizeUserId(String userId) {
        return userId.trim().toUpperCase();
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CustomException(message, HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}
