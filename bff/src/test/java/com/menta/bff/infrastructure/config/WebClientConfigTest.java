package com.menta.bff.infrastructure.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("WebClientConfig")
class WebClientConfigTest {

    private static final int ABOVE_DEFAULT_LIMIT = 1024 * 1024;

    private WebClientConfig config;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        AuthProperties auth = new AuthProperties();
        auth.setBaseUrl(wireMock.getHttpBaseUrl());
        VirtualApiProperties virtual = new VirtualApiProperties();
        virtual.setBaseUrl(wireMock.getHttpBaseUrl());
        BillingApiProperties billing = new BillingApiProperties();
        billing.setBaseUrl(wireMock.getHttpBaseUrl());
        config = new WebClientConfig(auth, virtual, billing);
        stubFor(get(urlEqualTo("/big")).willReturn(aResponse().withStatus(200)
                .withBody(new byte[ABOVE_DEFAULT_LIMIT])));
    }

    private static byte[] read(WebClient client) {
        return client.get().uri("/big").retrieve().bodyToMono(byte[].class).block();
    }

    @Test
    @DisplayName("the Billing client reads a body above Spring's 256 KB default, up to 6 MB")
    void billingClientHasTheRaisedLimit() {
        assertThat(WebClientConfig.BILLING_MAX_IN_MEMORY_BYTES).isEqualTo(6 * 1024 * 1024);
        assertThat(read(config.billingApiWebClient())).hasSize(ABOVE_DEFAULT_LIMIT);
    }

    @Test
    @DisplayName("the other clients keep the default limit")
    void otherClientsKeepTheDefaultLimit() {
        assertThatThrownBy(() -> read(config.webClient()))
                .hasRootCauseInstanceOf(DataBufferLimitException.class);
        assertThatThrownBy(() -> read(config.virtualApiWebClient()))
                .hasRootCauseInstanceOf(DataBufferLimitException.class);
    }
}
