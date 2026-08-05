package com.example.attendancesystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import com.example.attendancesystem.config.AdminBootstrap;

@SpringBootTest
@ActiveProfiles("test")
class AttendanceSystemApplicationTests {

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private AdminBootstrap adminBootstrap;

    @Test
    void contextLoads() {
    }

}



