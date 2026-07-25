package com.menta.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main Spring Boot application for Menta Dance.
 * Scans all modules under com.menta package.
 */
@SpringBootApplication(scanBasePackages = "com.menta")
@EnableJpaRepositories(basePackages = "com.menta")
@EntityScan(basePackages = "com.menta")
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class MentaDanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentaDanceApplication.class, args);
    }
}
