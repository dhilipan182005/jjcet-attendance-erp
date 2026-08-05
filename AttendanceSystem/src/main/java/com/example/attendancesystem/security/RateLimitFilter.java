package com.example.attendancesystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, RequestBucket> buckets = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS = 50;
    private static final long WINDOW_MS = 60000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = ClientIpResolver.resolve(request);
        RequestBucket bucket = buckets.computeIfAbsent(clientIp, k -> new RequestBucket());

        synchronized (bucket) {
            long now = System.currentTimeMillis();
            if (now - bucket.startTime > WINDOW_MS) {
                bucket.startTime = now;
                bucket.count = 0;
            }

            if (bucket.count >= MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
                return;
            }

            bucket.count++;
        }

        filterChain.doFilter(request, response);
    }

    private static class RequestBucket {
        long startTime = System.currentTimeMillis();
        int count = 0;
    }
}
