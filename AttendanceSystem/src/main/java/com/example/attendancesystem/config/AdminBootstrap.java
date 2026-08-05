package com.example.attendancesystem.config;

import com.example.attendancesystem.entity.Role;
import com.example.attendancesystem.entity.User;
import com.example.attendancesystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) 
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEFAULT_ADMIN_ID:ADMIN001}")
    private String adminId;

    @Value("${DEFAULT_ADMIN_PASSWORD:Jjcet@PlacementCell}")
    private String adminPassword;

    @Value("${DEFAULT_ADMIN_NAME:Placement Cell Administrator}")
    private String adminName;

    @Value("${DEFAULT_ADMIN_EMAIL:administration@jjcet.ac.in}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking bootstrap administrator configuration...");

        if (adminId == null || adminId.trim().isEmpty()) {
            log.warn("DEFAULT_ADMIN_ID is missing. Skipping bootstrap administrator creation.");
            return;
        }

        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            log.warn("DEFAULT_ADMIN_PASSWORD is missing. Skipping bootstrap administrator creation.");
            return;
        }

        String normalizedAdminId = adminId.trim().toUpperCase();
        String normalizedEmail = adminEmail != null ? adminEmail.trim().toLowerCase() : null;

        try {
            User userById = userRepository.findByUserIdIgnoreCase(normalizedAdminId).orElse(null);
            User userByEmail = normalizedEmail != null ? userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null) : null;

            if (userById != null && userByEmail != null && !userById.getId().equals(userByEmail.getId())) {
                log.error("Bootstrap conflict: DEFAULT_ADMIN_ID belongs to one account, but DEFAULT_ADMIN_EMAIL belongs to another. Manual intervention required.");
                return;
            }

            User bootstrapAdmin = userById != null ? userById : userByEmail;

            if (bootstrapAdmin == null) {
                User admin = User.builder()
                        .fullName(adminName != null ? adminName.trim() : "System Administrator")
                        .userId(normalizedAdminId)
                        .password(passwordEncoder.encode(adminPassword))
                        .email(normalizedEmail)
                        .role(Role.ADMIN)
                        .active(true)
                        .failedLoginAttempts(0)
                        .build();

                userRepository.save(admin);
                log.info("Bootstrap administrator created successfully with ADMIN role.");
            } else {
                log.info("Bootstrap administrator already exists.");
                boolean updated = false;

                if (bootstrapAdmin.getRole() != Role.ADMIN) {
                    log.warn("Bootstrap administrator is missing ADMIN authority. Restoring authority...");
                    bootstrapAdmin.setRole(Role.ADMIN);
                    updated = true;
                }

                if (!bootstrapAdmin.isActive()) {
                    log.warn("Bootstrap administrator is deactivated. Restoring active status...");
                    bootstrapAdmin.setActive(true);
                    updated = true;
                }
                if (updated) {
                    userRepository.save(bootstrapAdmin);
                    log.info("Bootstrap administrator authority restored successfully.");
                } else {
                    log.info("Bootstrap administrator authority is intact.");
                }
            }
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByUserIdIgnoreCase(normalizedAdminId)) {
                log.info("Bootstrap administrator already exists (created concurrently).");
            } else {
                log.error("Failed to create bootstrap administrator due to database constraint violation.", e);
                throw e; 
            }
        } catch (Exception e) {
            log.error("Failed to execute bootstrap administrator creation.", e);
            throw e;
        }
    }
}
