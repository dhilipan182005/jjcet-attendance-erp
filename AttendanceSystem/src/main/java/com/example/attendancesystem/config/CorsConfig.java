package com.example.attendancesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Correlation-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
