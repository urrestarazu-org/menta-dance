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

    /**
     * In-memory buffer limit of the Billing client: the API accepts transfer proofs of up to 5 MB, and Spring's
     * default of 256 KB would make reading one fail. The 1 MB of margin covers nothing but headroom; the limit
     * is deliberately not raised on the other clients.
     */
    public static final int BILLING_MAX_IN_MEMORY_BYTES = 6 * 1024 * 1024;

    private final AuthProperties authProperties;
    private final VirtualApiProperties virtualApiProperties;
    private final BillingApiProperties billingApiProperties;

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

    /**
     * Third, explicitly-named {@link WebClient} bean bound to the Billing
     * API's base URL, mirroring {@link #virtualApiWebClient()} exactly. The
     * bean name doubles as its implicit qualifier, so {@code BillingApiAdapter}
     * resolves it via {@code @Qualifier("billingApiWebClient")} on an explicit
     * constructor — Lombok's {@code @RequiredArgsConstructor} cannot carry a
     * per-parameter {@code @Qualifier}.
     */
    @Bean
    public WebClient billingApiWebClient() {
        return WebClient.builder()
                .baseUrl(billingApiProperties.getBaseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(BILLING_MAX_IN_MEMORY_BYTES))
                .build();
    }
}
