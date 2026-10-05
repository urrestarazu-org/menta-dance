package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class PhysicalClientFingerprintTest {

    private static final String SHA_256_HEX = "[0-9a-f]{64}";

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private static String sha(String value) {
        return new PhysicalClientFingerprint("10.0.0.0/8").device(value);
    }

    @Test
    void an_untrusted_peer_cannot_choose_its_own_fingerprint_through_x_forwarded_for() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        String spoofed = fingerprint.client(request("203.0.113.9", "198.51.100.1"));
        String plain = fingerprint.client(request("203.0.113.9", null));

        assertThat(spoofed).matches(SHA_256_HEX).isEqualTo(plain).isEqualTo(sha("203.0.113.9"));
    }

    @Test
    void a_trusted_proxy_contributes_the_last_x_forwarded_for_entry() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        String client = fingerprint.client(request("172.18.0.2", "6.6.6.6, 198.51.100.1"));

        assertThat(client).isEqualTo(sha("198.51.100.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " , ", ","})
    void a_trusted_proxy_without_a_usable_forwarded_entry_falls_back_to_the_peer(String header) {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        assertThat(fingerprint.client(request("172.18.0.2", header)))
            .isEqualTo(sha("172.18.0.2"));
    }

    @Test
    void a_trusted_proxy_without_the_header_falls_back_to_the_peer() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        assertThat(fingerprint.client(request("172.18.0.2", null))).isEqualTo(sha("172.18.0.2"));
    }

    @Test
    void trusted_proxy_matching_supports_several_cidrs_and_ignores_malformed_ones() {
        PhysicalClientFingerprint fingerprint =
            new PhysicalClientFingerprint("garbage, 10.0.0.0/99, 10.0.0.0/x, , 192.168.0.0/16");

        assertThat(fingerprint.client(request("192.168.4.4", "198.51.100.1")))
            .isEqualTo(sha("198.51.100.1"));
        assertThat(fingerprint.client(request("10.1.1.1", "198.51.100.1")))
            .isEqualTo(sha("10.1.1.1"));
    }

    @Test
    void an_ipv4_peer_is_never_matched_against_an_ipv6_cidr() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("::1/128");

        assertThat(fingerprint.client(request("172.18.0.2", "198.51.100.1")))
            .isEqualTo(sha("172.18.0.2"));
    }

    @Test
    void an_unparseable_peer_address_is_treated_as_untrusted() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        assertThat(fingerprint.client(request("[::zz]", "198.51.100.1")))
            .isEqualTo(sha("[::zz]"));
    }

    @Test
    void the_device_fingerprint_is_a_lowercase_sha_256_of_the_raw_device_id() {
        PhysicalClientFingerprint fingerprint = new PhysicalClientFingerprint("172.16.0.0/12");

        assertThat(fingerprint.device("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
