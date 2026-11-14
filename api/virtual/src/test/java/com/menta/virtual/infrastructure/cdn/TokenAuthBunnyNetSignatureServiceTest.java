package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit coverage for {@link TokenAuthBunnyNetSignatureService}.
 *
 * <p>Vector provenance: the {@code OFFICIAL_VECTOR} case is the "plain" vector published by Bunny.net in
 * {@code BunnyWay/BunnyCDN.TokenAuthentication} ({@code e2e/vectors.json}). The remaining expected tokens were
 * derived independently with {@code openssl dgst -sha256 -hmac <key> -binary | openssl base64 -A | tr '+/' '-_'
 * | tr -d '='} over {@code <path><expires>}, not with this implementation. All keys and hosts are fake.</p>
 */
class TokenAuthBunnyNetSignatureServiceTest {

    private static final String KEY = "test-security-key";
    private static final long EXPIRES = 1_700_000_000L;

    private static BunnyNetProperties properties(String host) {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname(host);
        properties.setVideoLibraryId("123456");
        properties.setSecurityKey(KEY);
        return properties;
    }

    private static TokenAuthBunnyNetSignatureService service(String host) {
        return new TokenAuthBunnyNetSignatureService(properties(host));
    }

    @Test
    void token_matches_the_official_bunny_vector() {
        // Key "SecurityKey", path "/300kb.jpg", expires 1598024587 (BunnyCDN.TokenAuthentication e2e/vectors.json).
        assertThat(TokenAuthBunnyNetSignatureService.token("SecurityKey", "/300kb.jpg", 1_598_024_587L))
            .isEqualTo("HS256-o10JRWlsAItyAsdKS6jJKjabHN4FrFsplDHPV1idcX4");
    }

    @Test
    void signs_the_mp4_url_with_the_independently_derived_vector() {
        assertThat(service("vz-example.b-cdn.net").generateSignedUrl("vid-1", EXPIRES))
            .isEqualTo("https://vz-example.b-cdn.net/vid-1/play_720p.mp4"
                + "?token=HS256-pkB2m9clwQnq5mFxdYJw09vMo9ymIw8e4-vYX2bn_Ko&expires=1700000000");
    }

    @Test
    void expiry_is_the_value_given_and_part_of_the_signature() {
        TokenAuthBunnyNetSignatureService service = service("vz-example.b-cdn.net");

        String first = service.generateSignedUrl("vid-1", EXPIRES);
        String second = service.generateSignedUrl("vid-1", EXPIRES + 1);

        assertThat(second).contains("&expires=1700000001").isNotEqualTo(first);
        assertThat(tokenOf(second)).isNotEqualTo(tokenOf(first));
    }

    @Test
    void the_library_id_is_not_part_of_the_url() {
        assertThat(service("vz-example.b-cdn.net").generateSignedUrl("vid-1", EXPIRES))
            .doesNotContain("123456");
    }

    @Test
    void file_name_is_configurable() {
        BunnyNetProperties properties = properties("vz-example.b-cdn.net");
        properties.setFileName("play_480p.mp4");

        assertThat(new TokenAuthBunnyNetSignatureService(properties).generateSignedUrl("vid-1", EXPIRES))
            .startsWith("https://vz-example.b-cdn.net/vid-1/play_480p.mp4?token=HS256-");
    }

    @Test
    void special_characters_are_encoded_in_the_url_but_signed_decoded() {
        // Signed path (decoded): "/ñ a/play_720p.mp4", as the reference implementation signs uri.getPath().
        assertThat(service("vz-example.b-cdn.net").generateSignedUrl("ñ a", EXPIRES))
            .isEqualTo("https://vz-example.b-cdn.net/%C3%B1%20a/play_720p.mp4"
                + "?token=HS256-fbKs0tp6tqyhX7zAUpOhMvMX1rCDNMZUqzaPdnMQvLE&expires=1700000000");
    }

    @Test
    void file_name_special_characters_are_encoded_and_signed_decoded() {
        BunnyNetProperties properties = properties("vz-example.b-cdn.net");
        properties.setFileName("clip (1).mp4");

        assertThat(new TokenAuthBunnyNetSignatureService(properties).generateSignedUrl("Vid_ABC-9", EXPIRES))
            .isEqualTo("https://vz-example.b-cdn.net/Vid_ABC-9/clip%20%281%29.mp4"
                + "?token=HS256-7UJtc2XR8obC5PzPT-N9wsTox3fO1fE-pPcNI2yqUNY&expires=1700000000");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "vz-example.b-cdn.net", "https://vz-example.b-cdn.net", "HTTPS://vz-example.b-cdn.net",
        "http://vz-example.b-cdn.net", "vz-example.b-cdn.net/", "https://vz-example.b-cdn.net///",
        "  https://vz-example.b-cdn.net  "
    })
    void host_is_normalised_to_https_without_trailing_slash(String host) {
        assertThat(service(host).generateSignedUrl("vid-1", EXPIRES))
            .startsWith("https://vz-example.b-cdn.net/vid-1/play_720p.mp4?token=");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "/"})
    void blank_video_id_is_rejected(String videoId) {
        assertThatThrownBy(() -> service("vz-example.b-cdn.net").generateSignedUrl(videoId, EXPIRES))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("videoId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void non_positive_expiry_is_rejected(long expiry) {
        assertThatThrownBy(() -> service("vz-example.b-cdn.net").generateSignedUrl("vid-1", expiry))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expirationTimeInSeconds");
    }

    @Test
    void blank_security_key_fails_without_echoing_anything_secret() {
        BunnyNetProperties properties = properties("vz-example.b-cdn.net");
        properties.setSecurityKey(" ");

        assertThatThrownBy(() -> new TokenAuthBunnyNetSignatureService(properties).generateSignedUrl("vid-1", EXPIRES))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("security-key");
    }

    @Test
    void the_security_key_never_appears_in_the_url() {
        assertThat(service("vz-example.b-cdn.net").generateSignedUrl("vid-1", EXPIRES)).doesNotContain(KEY);
    }

    @Test
    void signing_logs_a_secret_free_info_line_with_video_host_file_and_expiry() {
        try (CapturedLogs logs = CapturedLogs.of(TokenAuthBunnyNetSignatureService.class)) {
            String url = service("https://vz-example.b-cdn.net/").generateSignedUrl("vid-1", EXPIRES);

            assertThat(logs.messages(Level.INFO)).containsExactly(
                "Bunny.net signed URL: videoId=vid-1 host=vz-example.b-cdn.net file=play_720p.mp4"
                    + " expiresAt=2023-11-14T22:13:20Z");
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.text())
                .doesNotContain(KEY)
                .doesNotContain(tokenOf(url))
                .doesNotContain("HS256")
                .doesNotContain("token=")
                .doesNotContain("expires=")
                .doesNotContain("https://")
                .doesNotContain(url);
        }
    }

    @Test
    void an_expiry_beyond_the_instant_range_still_signs_and_is_logged_as_out_of_range() {
        try (CapturedLogs logs = CapturedLogs.of(TokenAuthBunnyNetSignatureService.class)) {
            String url = service("vz-example.b-cdn.net").generateSignedUrl("vid-1", Long.MAX_VALUE);

            assertThat(url).contains("&expires=" + Long.MAX_VALUE);
            assertThat(logs.messages(Level.INFO)).singleElement().asString()
                .endsWith("expiresAt=out-of-range(" + Long.MAX_VALUE + ")");
        }
    }

    @Test
    void rejected_video_id_is_logged_at_warn_without_secrets_before_throwing() {
        try (CapturedLogs logs = CapturedLogs.of(TokenAuthBunnyNetSignatureService.class)) {
            assertThatThrownBy(() -> service("vz-example.b-cdn.net").generateSignedUrl("a/b", EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);

            assertThat(logs.messages(Level.WARN)).containsExactly(
                "Bunny.net URL signing rejected: videoId is blank or not a single path segment");
            assertThat(logs.messages(Level.INFO)).isEmpty();
            assertThat(logs.text()).doesNotContain(KEY);
        }
    }

    @Test
    void rejected_expiry_is_logged_at_warn_without_secrets_before_throwing() {
        try (CapturedLogs logs = CapturedLogs.of(TokenAuthBunnyNetSignatureService.class)) {
            assertThatThrownBy(() -> service("vz-example.b-cdn.net").generateSignedUrl("vid-1", 0L))
                .isInstanceOf(IllegalArgumentException.class);

            assertThat(logs.messages(Level.WARN)).containsExactly(
                "Bunny.net URL signing rejected: expirationTimeInSeconds is not a positive epoch-second instant");
            assertThat(logs.text()).doesNotContain(KEY);
        }
    }

    @Test
    void missing_security_key_is_logged_at_warn_naming_only_the_property() {
        BunnyNetProperties properties = properties("vz-example.b-cdn.net");
        properties.setSecurityKey(" ");

        try (CapturedLogs logs = CapturedLogs.of(TokenAuthBunnyNetSignatureService.class)) {
            assertThatThrownBy(() -> new TokenAuthBunnyNetSignatureService(properties)
                .generateSignedUrl("vid-1", EXPIRES)).isInstanceOf(IllegalStateException.class);

            assertThat(logs.messages(Level.WARN)).containsExactly(
                "Bunny.net URL signing rejected: app.cdn.bunny-net.security-key is not configured");
        }
    }

    private static String tokenOf(String url) {
        String afterToken = url.substring(url.indexOf("token=") + "token=".length());
        return afterToken.substring(0, afterToken.indexOf('&'));
    }
}
