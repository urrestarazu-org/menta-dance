package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * ADR-0035 trust boundary, BFF hop.
 *
 * <p>NGINX sets {@code X-Real-IP $remote_addr} with {@code proxy_set_header},
 * which overwrites whatever the caller sent. That makes the header a canonical
 * value — but only across a hop that actually went through NGINX. Trusting it
 * from an arbitrary peer would let anyone rotate their own origin identity at
 * will and walk straight past a per-origin budget.</p>
 */
class ClientOriginResolverTest {

    private static final String TRUSTED_CIDRS = "172.16.0.0/12";
    private static final String NGINX_ADDRESS = "172.18.0.5";
    private static final String USER_ADDRESS = "203.0.113.9";

    private final ClientOriginResolver resolver = new ClientOriginResolver(TRUSTED_CIDRS);

    private static MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Nested
    @DisplayName("Trusted peer")
    class TrustedPeer {

        @Test
        void uses_the_address_nginx_sanitised() {
            MockHttpServletRequest request = requestFrom(NGINX_ADDRESS);
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(resolver.resolve(request)).isEqualTo(USER_ADDRESS);
        }

        @Test
        void falls_back_to_the_peer_when_the_header_is_absent() {
            assertThat(resolver.resolve(requestFrom(NGINX_ADDRESS))).isEqualTo(NGINX_ADDRESS);
        }

        @Test
        void falls_back_to_the_peer_when_the_header_is_blank() {
            MockHttpServletRequest request = requestFrom(NGINX_ADDRESS);
            request.addHeader("X-Real-IP", "   ");

            assertThat(resolver.resolve(request)).isEqualTo(NGINX_ADDRESS);
        }

        @Test
        void ignores_the_forwarded_for_chain_entirely() {
            // ADR-0035: the BFF must not parse proxy chains. Reading
            // X-Forwarded-For here would reintroduce the ambiguity that
            // X-Real-IP exists to avoid.
            MockHttpServletRequest request = requestFrom(NGINX_ADDRESS);
            request.addHeader("X-Forwarded-For", "198.51.100.7, " + USER_ADDRESS);

            assertThat(resolver.resolve(request)).isEqualTo(NGINX_ADDRESS);
        }
    }

    @Nested
    @DisplayName("Untrusted peer")
    class UntrustedPeer {

        @Test
        void ignores_a_spoofed_header_and_uses_the_real_peer() {
            // ADR-0035 mandatory test: "un header X-Real-IP enviado
            // directamente al BFF se ignora cuando el peer no es confiable".
            MockHttpServletRequest request = requestFrom(USER_ADDRESS);
            request.addHeader("X-Real-IP", "10.0.0.1");

            assertThat(resolver.resolve(request)).isEqualTo(USER_ADDRESS);
        }

        @Test
        void cannot_be_rotated_by_changing_the_header() {
            // The whole point of a per-origin budget: an attacker must not be
            // able to present a fresh identity on every request.
            MockHttpServletRequest first = requestFrom(USER_ADDRESS);
            first.addHeader("X-Real-IP", "10.0.0.1");
            MockHttpServletRequest second = requestFrom(USER_ADDRESS);
            second.addHeader("X-Real-IP", "10.0.0.2");

            assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
        }
    }

    @Nested
    @DisplayName("Trusted CIDR configuration")
    class CidrConfiguration {

        @Test
        void accepts_several_trusted_ranges() {
            ClientOriginResolver multi = new ClientOriginResolver("10.0.0.0/8, 192.168.0.0/16");
            MockHttpServletRequest request = requestFrom("192.168.4.4");
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(multi.resolve(request)).isEqualTo(USER_ADDRESS);
        }

        @Test
        void an_empty_configuration_trusts_nobody() {
            // Fail closed: a missing configuration must not silently mean
            // "trust every peer".
            ClientOriginResolver none = new ClientOriginResolver("");
            MockHttpServletRequest request = requestFrom(NGINX_ADDRESS);
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(none.resolve(request)).isEqualTo(NGINX_ADDRESS);
        }

        @Test
        void a_malformed_entry_never_widens_trust() {
            ClientOriginResolver malformed = new ClientOriginResolver("not-a-cidr, 172.16.0.0/12");
            MockHttpServletRequest trusted = requestFrom(NGINX_ADDRESS);
            trusted.addHeader("X-Real-IP", USER_ADDRESS);
            MockHttpServletRequest untrusted = requestFrom(USER_ADDRESS);
            untrusted.addHeader("X-Real-IP", "10.0.0.1");

            assertThat(malformed.resolve(trusted)).isEqualTo(USER_ADDRESS);
            assertThat(malformed.resolve(untrusted)).isEqualTo(USER_ADDRESS);
        }

        @Test
        void a_null_configuration_trusts_nobody() {
            ClientOriginResolver nullConfig = new ClientOriginResolver(null);
            MockHttpServletRequest request = requestFrom(NGINX_ADDRESS);
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(nullConfig.resolve(request)).isEqualTo(NGINX_ADDRESS);
        }

        @Test
        void a_prefix_length_wider_than_the_address_never_widens_trust() {
            ClientOriginResolver invalidPrefix = new ClientOriginResolver("10.0.0.0/33");
            MockHttpServletRequest request = requestFrom("10.0.0.5");
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(invalidPrefix.resolve(request)).isEqualTo("10.0.0.5");
        }

        @Test
        void a_non_numeric_prefix_never_widens_trust() {
            ClientOriginResolver invalidPrefix = new ClientOriginResolver("10.0.0.0/not-a-number");
            MockHttpServletRequest request = requestFrom("10.0.0.5");
            request.addHeader("X-Real-IP", USER_ADDRESS);

            assertThat(invalidPrefix.resolve(request)).isEqualTo("10.0.0.5");
        }
    }
}
