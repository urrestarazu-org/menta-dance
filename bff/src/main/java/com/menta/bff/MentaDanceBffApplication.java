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

    public static void main(String[] args) {
        SpringApplication.run(MentaDanceBffApplication.class, args);
    }
}
