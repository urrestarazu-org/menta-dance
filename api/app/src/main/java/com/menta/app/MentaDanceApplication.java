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

    /**
     * Boots the full Menta Dance application.
     *
     * <p>Component, JPA repository and entity scanning all cover the whole {@code com.menta}
     * package, so every module on the classpath is assembled into this single process.</p>
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MentaDanceApplication.class, args);
    }
}
