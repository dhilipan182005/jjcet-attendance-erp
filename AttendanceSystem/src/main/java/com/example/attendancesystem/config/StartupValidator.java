package com.example.attendancesystem.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class StartupValidator {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @PostConstruct
    public void validateConfiguration() {
        if (!"prod".equalsIgnoreCase(activeProfile)) {
            log.info("Running in profile '{}'. Skipping strict production validations.", activeProfile);
            return;
        }

        log.info("Validating production configuration...");
        List<String> missingConfigs = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            missingConfigs.add("JWT_SECRET");
        } else if (jwtSecret.length() < 32) {
            log.error("JWT_SECRET must be at least 32 characters long for production security");
            missingConfigs.add("JWT_SECRET (Too short)");
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            missingConfigs.add("DATABASE_URL");
        } else if (dbUrl.contains("h2:mem")) {
            log.error("H2 memory database is not allowed in production");
            missingConfigs.add("DATABASE_URL (Invalid DB)");
        }

        if (dbPassword == null || dbPassword.isBlank()) {
            missingConfigs.add("DATABASE_PASSWORD");
        }

        if (!missingConfigs.isEmpty()) {
            log.error("========================================");
            log.error("STARTUP FAILED: Missing or invalid required production configuration!");
            for (String config : missingConfigs) {
                log.error("- {}", config);
            }
            log.error("========================================");
            throw new IllegalStateException("Missing required configuration: " + missingConfigs);
        }

        log.info("Production configuration validated successfully.");
    }
}
