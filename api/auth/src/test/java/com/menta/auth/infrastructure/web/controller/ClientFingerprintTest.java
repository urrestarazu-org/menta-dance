package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientFingerprintTest {

    @Test
    void uses_forwarded_client_ip_only_when_the_immediate_peer_is_trusted() {
        MockHttpServletRequest request = request("172.20.0.5", "10.0.0.8, 198.51.100.44");

        assertThat(ClientFingerprint.sourceAddress(request, "172.16.0.0/12")).isEqualTo("198.51.100.44");
    }

    @Test
    void falls_back_to_direct_peer_without_a_forwarding_header() {
        MockHttpServletRequest request = request("203.0.113.4", null);

        assertThat(ClientFingerprint.sourceAddress(request, "172.16.0.0/12")).isEqualTo("203.0.113.4");
    }

    @Test
    void ignores_spoofed_forwarded_header_from_an_untrusted_peer() {
        MockHttpServletRequest request = request("203.0.113.4", "198.51.100.44");

        assertThat(ClientFingerprint.sourceAddress(request, "172.16.0.0/12")).isEqualTo("203.0.113.4");
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
