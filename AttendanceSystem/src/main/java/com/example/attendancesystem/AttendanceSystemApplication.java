package com.example.attendancesystem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class AttendanceSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceSystemApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("========================================");
        log.info("APPLICATION STARTED");
        log.info("DATABASE CONNECTED");
        log.info("JWT INITIALIZED");
        log.info("CORS LOADED");
        log.info("========================================");
    }
}
