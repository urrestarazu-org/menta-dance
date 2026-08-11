package com.menta.bff.infrastructure.config;

import com.menta.bff.infrastructure.adapter.SpringSessionTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Configuration for session-related beans.
 */
@Configuration
public class SessionConfig {

    /**
     * Provides SpringSessionTokenRepository with request-scoped HttpSession supplier.
     * <p>
     * The supplier lazily evaluates to avoid accessing session outside request context.
     * </p>
     */
    @Bean
    @RequestScope
    public SpringSessionTokenRepository springSessionTokenRepository(HttpServletRequest request) {
        // Use getSession(true) to create session if it doesn't exist
        // This is needed during login when Spring Security hasn't created the session yet
        return new SpringSessionTokenRepository(() -> request.getSession(true));
    }
}
