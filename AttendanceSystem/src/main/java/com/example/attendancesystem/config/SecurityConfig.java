package com.example.attendancesystem.config;

import com.example.attendancesystem.security.CustomUserDetailsService;
import com.example.attendancesystem.security.JwtFilter;
import com.example.attendancesystem.security.JwtUtil;
import com.example.attendancesystem.security.RateLimitFilter;
import com.example.attendancesystem.security.SessionTimeoutFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final SessionTimeoutFilter sessionTimeoutFilter;
    private final RateLimitFilter rateLimitFilter;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Bean
    public JwtFilter jwtFilter() {
        return new JwtFilter(jwtUtil, userDetailsService);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {

        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .headers(headers -> headers
                        .xssProtection(org.springframework.security.config.Customizer.withDefaults())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data:; frame-ancestors 'self';"))
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .cacheControl(cache -> cache.disable())
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/assets/**", "/favicon.ico", "/api/auth/**", "/actuator/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/departments", "/api/admin/batches").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/teacher/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/attendance/**").hasAnyRole("ADMIN", "TEACHER")
                        .anyRequest().authenticated()
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(sessionTimeoutFilter, JwtFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            com.example.attendancesystem.dto.response.ErrorResponse err = com.example.attendancesystem.dto.response.ErrorResponse.builder()
                                .status(401)
                                .error("Unauthorized")
                                .code("AUTHENTICATION_REQUIRED")
                                .message("Authentication required. Please log in.")
                                .path(req.getRequestURI())
                                .build();
                            res.getWriter().write(objectMapper.writeValueAsString(err));
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            com.example.attendancesystem.dto.response.ErrorResponse err = com.example.attendancesystem.dto.response.ErrorResponse.builder()
                                .status(403)
                                .error("Forbidden")
                                .code("ACCESS_DENIED")
                                .message("You do not have permission to access this resource.")
                                .path(req.getRequestURI())
                                .build();
                            res.getWriter().write(objectMapper.writeValueAsString(err));
                        })
                );

        return http.build();
    }
}
