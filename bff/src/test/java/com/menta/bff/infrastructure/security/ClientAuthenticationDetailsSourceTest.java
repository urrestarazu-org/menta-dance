package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * End of the BFF resolution chain: what the details source captures is exactly
 * what the provider will forward to the Auth API (ADR-0035).
 */
class ClientAuthenticationDetailsSourceTest {

    private static final String NGINX_ADDRESS = "172.18.0.5";
    private static final String USER_ADDRESS = "203.0.113.9";

    private final ClientAuthenticationDetailsSource source =
        new ClientAuthenticationDetailsSource(new ClientOriginResolver("172.16.0.0/12"));

    @Test
    void captures_the_resolved_origin_behind_a_trusted_proxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(NGINX_ADDRESS);
        request.addHeader("X-Real-IP", USER_ADDRESS);

        assertThat(source.buildDetails(request).clientAddress()).isEqualTo(USER_ADDRESS);
    }

    @Test
    void captures_the_peer_when_the_request_did_not_come_through_a_trusted_proxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(USER_ADDRESS);
        request.addHeader("X-Real-IP", "10.0.0.1");

        assertThat(source.buildDetails(request).clientAddress()).isEqualTo(USER_ADDRESS);
    }

    @Test
    void two_different_clients_produce_two_different_origins() {
        // ADR-0035 mandatory test: distinct IPs through NGINX must not collapse
        // into one identity — that collapse is the whole defect being fixed.
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr(NGINX_ADDRESS);
        first.addHeader("X-Real-IP", "203.0.113.9");
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setRemoteAddr(NGINX_ADDRESS);
        second.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(source.buildDetails(first).clientAddress())
            .isNotEqualTo(source.buildDetails(second).clientAddress());
    }
}
