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
    private final VirtualApiProperties virtualApiProperties;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(authProperties.getBaseUrl())
                .build();
    }

    /**
     * Second, explicitly-named {@link WebClient} bean bound to the Virtual/Catalog
     * API's base URL (design decision C). The bean name doubles as its implicit
     * qualifier, so {@code VirtualApiAdapter} resolves it via
     * {@code @Qualifier("virtualApiWebClient")} on an explicit constructor — Lombok's
     * {@code @RequiredArgsConstructor} cannot carry the qualifier. The pre-existing
     * unqualified {@link #webClient()} bean and {@code AuthApiAdapter}'s
     * parameter-name resolution stay untouched.
     */
    @Bean
    public WebClient virtualApiWebClient() {
        return WebClient.builder()
                .baseUrl(virtualApiProperties.getBaseUrl())
                .build();
    }
}
