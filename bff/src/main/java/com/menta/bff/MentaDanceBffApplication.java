package com.menta.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Menta Dance BFF (Backend for Frontend).
 * Serves web pages using Thymeleaf and communicates with the API.
 */
@SpringBootApplication
public final class MentaDanceBffApplication {

    private MentaDanceBffApplication() {
        // Private constructor to prevent instantiation
    }

    /**
     * Boots the BFF Spring context.
     *
     * <p>The application class is a non-instantiable launcher, so this entry point is the only way
     * to start it.</p>
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MentaDanceBffApplication.class, args);
    }
}
