package com.menta.bff;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test to verify BFF application context loads successfully.
 */
@SpringBootTest
class MentaDanceBffApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context loads
        // without errors.
    }

    @Test
    void main_delegates_to_spring_application_run() {
        String[] args = {"--server.port=0"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(eq(MentaDanceBffApplication.class), eq(args)))
                    .thenReturn(null);

            MentaDanceBffApplication.main(args);

            springApplication.verify(() ->
                    SpringApplication.run(MentaDanceBffApplication.class, args));
        }
    }
}
