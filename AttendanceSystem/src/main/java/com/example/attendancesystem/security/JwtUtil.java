package com.example.attendancesystem.security;

import com.example.attendancesystem.exception.CustomException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private static final String ISSUER = "attendance-system";

    private Key getSignKey() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String role, Long teacherId) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (teacherId != null) {
            claims.put("teacherId", teacherId);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuer(ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = parseClaims(token);
        return resolver.apply(claims);
    }

    private Claims parseClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .requireIssuer(ISSUER)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims;

        } catch (ExpiredJwtException e) {
            throw new CustomException("Token expired", HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        }
    }

    public boolean validateToken(String token, String userId) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equalsIgnoreCase(userId) && !isExpired(claims);
        } catch (CustomException e) {
            return false;
        }
    }

    public boolean validateToken(String token,
                                 org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equalsIgnoreCase(userDetails.getUsername())
                    && !isExpired(claims);
        } catch (CustomException e) {
            return false;
        }
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
