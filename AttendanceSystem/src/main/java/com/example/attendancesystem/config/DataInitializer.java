package com.example.attendancesystem.config;

import com.example.attendancesystem.entity.*;
import com.example.attendancesystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DataInitializer {

    private final DepartmentRepository departmentRepository;
    private final BatchRepository batchRepository;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            try {
                log.info("Starting database seed process...");

            // Database seeding for default domain data has been removed.
            // Administrator must configure Departments and Batches explicitly.

            log.info("Database seed process completed successfully.");

            } catch (Exception e) {
                log.error("Failed to execute database seeder. This may happen if the database is temporarily unreachable: {}", e.getMessage(), e);
            }
        };
    }
}
