package com.example.attendancesystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        String userId;

        try {
            userId = jwtUtil.extractUserId(token);
        } catch (Exception e) {
            log.error("JwtFilter extractUserId failed for token: {}", e.getMessage(), e);
            chain.doFilter(request, response);
            return;
        }

        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(userId);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                log.error("JwtFilter loadUserByUsername failed for userId: {}", userId, e);
                chain.doFilter(request, response);
                return;
            }

            if (jwtUtil.validateToken(token, userDetails)) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.error("JwtFilter validateToken returned false for userId: {}", userId);
            }
        }

        chain.doFilter(request, response);
    }
}
