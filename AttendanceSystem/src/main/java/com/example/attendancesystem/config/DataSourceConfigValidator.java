package com.example.attendancesystem.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration
@Profile("prod")
public class DataSourceConfigValidator {

    private final Environment environment;

    public DataSourceConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateProperty("DATABASE_URL");
        validateProperty("DATABASE_USERNAME");
        validateProperty("DATABASE_PASSWORD");

        log.info("ACTIVE PROFILE: prod");
        log.info("DATABASE URL CONFIGURED: YES");
        log.info("DATABASE USERNAME CONFIGURED: YES");
        log.info("DATABASE PASSWORD CONFIGURED: YES");

        try {
            Class.forName("org.postgresql.Driver");
            log.info("POSTGRES DRIVER AVAILABLE: YES");
        } catch (ClassNotFoundException e) {
            log.warn("POSTGRES DRIVER AVAILABLE: NO");
        }
    }

    private void validateProperty(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.trim().isEmpty() || value.contains("${")) {
            log.error("DATABASE CONFIGURATION ERROR: {} is missing.", propertyName);
            throw new IllegalStateException("Missing required production database configuration: " + propertyName);
        }
    }
}
