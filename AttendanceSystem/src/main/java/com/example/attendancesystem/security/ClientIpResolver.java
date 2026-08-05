package com.example.attendancesystem.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind a reverse proxy (Render, Firebase, etc). Using
 * request.getRemoteAddr() directly behind such a proxy returns the proxy's own address for
 * every request, which previously made RateLimitFilter bucket every user on the platform
 * together under one 50-requests/minute limit. AuditService already had this exact logic
 * inline; it's centralized here so both use the same, correct behavior.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ip = firstNonEmpty(request.getHeader("X-Forwarded-For"));
        if (ip == null) ip = firstNonEmpty(request.getHeader("Proxy-Client-IP"));
        if (ip == null) ip = firstNonEmpty(request.getHeader("WL-Proxy-Client-IP"));
        if (ip == null) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "UNKNOWN";
    }

    private static String firstNonEmpty(String value) {
        if (value == null || value.isEmpty() || "unknown".equalsIgnoreCase(value)) return null;
        return value;
    }
}
