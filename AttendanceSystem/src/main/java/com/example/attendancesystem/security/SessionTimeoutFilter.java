package com.example.attendancesystem.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionTimeoutFilter implements Filter {

    private static final ConcurrentHashMap<String, LocalDateTime> lastActivityMap = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (request.getRequestURI().endsWith("/auth/logout")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String userId = auth.getName();
                lastActivityMap.remove(userId);
            }
            response.setHeader("Clear-Site-Data", "\"storage\"");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            String userId = auth.getName();
            long timeoutMinutes = 30;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastActive = lastActivityMap.get(userId);

            if (lastActive != null) {
                long minutesElapsed = Duration.between(lastActive, now).toMinutes();
                if (minutesElapsed >= timeoutMinutes) {
                    log.info("Session expired for user: {} due to inactivity ({} mins)", userId, minutesElapsed);
                    lastActivityMap.remove(userId);
                    SecurityContextHolder.clearContext();

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setHeader("Clear-Site-Data", "\"storage\"");
                    response.getWriter().write("{\"success\": false, \"status\": 401, \"message\": \"Session expired due to inactivity.\", \"code\": \"SESSION_EXPIRED\"}");
                    return;
                }
            }
            lastActivityMap.put(userId, now);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
