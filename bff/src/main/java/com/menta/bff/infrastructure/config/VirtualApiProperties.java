package com.menta.bff.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;

/**
 * Configuration properties for the Virtual/Catalog API client.
 * <p>
 * Binds to application.yml properties prefixed with {@code menta.api}.
 * </p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "menta.api")
public class VirtualApiProperties {

    /**
     * Base URL of the api:app catalog/virtual endpoints (e.g., http://localhost:8081).
     */
    @NotBlank(message = "Virtual API base URL must be configured")
    private String baseUrl;

    /**
     * HTTP request timeout for Virtual API calls.
     * Default: 5 seconds.
     */
    private Duration timeout = Duration.ofSeconds(5);
}
