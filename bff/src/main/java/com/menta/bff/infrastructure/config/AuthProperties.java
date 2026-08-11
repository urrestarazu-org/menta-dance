package com.menta.bff.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;

/**
 * Configuration properties for Auth API client.
 * <p>
 * Binds to application.yml properties prefixed with {@code menta.auth}.
 * </p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "menta.auth")
public class AuthProperties {

    /**
     * Base URL of the Auth API (e.g., http://localhost:8081).
     */
    @NotBlank(message = "Auth API base URL must be configured")
    private String baseUrl;

    /**
     * HTTP request timeout for Auth API calls.
     * Default: 5 seconds.
     */
    private Duration timeout = Duration.ofSeconds(5);
}
