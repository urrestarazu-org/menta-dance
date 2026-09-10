package com.menta.bff.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;

/**
 * Configuration properties for the Billing API client.
 * <p>
 * Binds to application.yml properties prefixed with {@code menta.billing}.
 * </p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "menta.billing")
public class BillingApiProperties {

    /**
     * Base URL of the api:app billing endpoints (e.g., http://localhost:8081).
     */
    @NotBlank(message = "Billing API base URL must be configured")
    private String baseUrl;

    /**
     * HTTP request timeout for Billing API calls.
     * Default: 5 seconds.
     */
    private Duration timeout = Duration.ofSeconds(5);

    /**
     * How long a successful plans response is cached before a fresh upstream
     * call is required (design D4).
     * Default: 5 minutes.
     */
    private Duration cacheTtl = Duration.ofMinutes(5);
}
