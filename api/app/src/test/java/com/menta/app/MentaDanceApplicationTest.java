package com.menta.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify Spring Boot application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class MentaDanceApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context loads
        // without errors. If the context fails to load, this test will fail.
    }
}
