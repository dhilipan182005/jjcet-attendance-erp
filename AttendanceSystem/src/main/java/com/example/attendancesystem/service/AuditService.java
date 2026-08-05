package com.example.attendancesystem.service;

import com.example.attendancesystem.entity.AuditLog;
import com.example.attendancesystem.repository.AuditLogRepository;
import com.example.attendancesystem.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public String getCurrentUserEmail() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve user email: {}", e.getMessage());
        }
        return "SYSTEM";
    }

    public void logAction(String action) {
        logAction(action, null);
    }

    public void logAction(String action, String entityName) {
        String email = getCurrentUserEmail();
        String ip = getClientIp();
        String device = getDeviceType();
        String role = getCurrentUserRole();

        log.info("AUDIT | action='{}' | entity='{}' | user={} | role={} | ip={} | device={}",
                action, entityName, email, role, ip, device);

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .userEmail(email)
                .userId(email)
                .action(action)
                .timestamp(LocalDateTime.now())
                .ipAddress(ip);

        builder.entityName(entityName);
        builder.role(role);
        builder.deviceType(device);

        try {
            java.util.Optional<AuditLog> lastLog = auditLogRepository.findTopByUserIdAndActionOrderByTimestampDesc(email, action);
            if (lastLog.isPresent()) {
                AuditLog prev = lastLog.get();
                if (prev.getTimestamp() != null && java.time.Duration.between(prev.getTimestamp(), LocalDateTime.now()).toMillis() < 2000) {
                    if (entityName == null || entityName.equals(prev.getEntityName())) {
                        log.info("Skipping duplicate audit log: action='{}' by user='{}'", action, email);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            auditLogRepository.save(builder.build());
        } catch (Exception e) {
            log.warn("Failed to persist audit log: {}", e.getMessage());
        }
    }

    private String getCurrentUserRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                return auth.getAuthorities().iterator().next().getAuthority();
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return ClientIpResolver.resolve(attributes.getRequest());
            }
        } catch (Exception e) {
            log.warn("Could not retrieve client IP: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    private String getDeviceType() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String ua = attributes.getRequest().getHeader("User-Agent");
                if (ua == null) return "UNKNOWN";
                ua = ua.toLowerCase();
                if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "MOBILE";
                if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
                return "DESKTOP";
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }
}
