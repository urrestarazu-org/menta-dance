package com.menta.bff.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used to call Auth API.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final AuthProperties authProperties;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(authProperties.getBaseUrl())
                .build();
    }
}
