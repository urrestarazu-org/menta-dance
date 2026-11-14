package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Cover for {@link BunnyNetProperties}: placeholder defaults, the fail-closed check used when the real
 * token-authentication adapter is selected, and the guarantee that the security key is never rendered.
 *
 * <p>The properties carry no bean-validation annotations so a context can boot under the {@code demo} and
 * {@code e2e-bunny-net} profiles without any {@code app.cdn.bunny-net.*} value; the check is applied by
 * {@code VirtualConfiguration} only when the real adapter is selected.</p>
 */
class BunnyNetPropertiesValidationTest {

    private static BunnyNetProperties valid() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-example.b-cdn.net");
        properties.setVideoLibraryId("123456");
        properties.setSecurityKey("test-security-key");
        return properties;
    }

    @Test
    void defaults_are_flagged_placeholders_and_the_key_is_absent() {
        BunnyNetProperties properties = new BunnyNetProperties();

        assertThat(properties.getPullZoneHostname()).contains("invalid-missing-config");
        assertThat(properties.getVideoLibraryId()).isEqualTo("missing-config");
        assertThat(properties.getSecurityKey()).isBlank();
        assertThat(properties.getFileName()).isEqualTo("play_720p.mp4");
        assertThat(properties.isDiagnosticProbe()).isFalse();
        assertThat(properties.getDiagnosticProbeReferer()).isBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/", "https://www.example.com", "HTTP://localhost/path?x=1"})
    void an_absolute_http_or_https_probe_referer_passes_the_check_when_the_probe_is_on(String referer) {
        BunnyNetProperties properties = valid();
        properties.setDiagnosticProbe(true);
        properties.setDiagnosticProbeReferer(referer);

        assertThatCode(properties::requireRealCdnConfiguration).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void a_blank_probe_referer_is_valid_and_means_no_referer(String referer) {
        BunnyNetProperties properties = valid();
        properties.setDiagnosticProbe(true);
        properties.setDiagnosticProbeReferer(referer);

        assertThatCode(properties::requireRealCdnConfiguration).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080", "localhost", "ftp://example.com/", "http://", "/relative/path",
        "http://exa mple.com/", "not a url", "http:///no-host"})
    void an_invalid_probe_referer_fails_the_check_only_when_the_probe_is_on(String referer) {
        BunnyNetProperties properties = valid();
        properties.setDiagnosticProbe(true);
        properties.setDiagnosticProbeReferer(referer);

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.diagnostic-probe-referer")
            .hasMessageContaining("APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER")
            .hasMessageContaining("absolute http(s) URL")
            .hasMessageNotContaining("security-key");
    }

    @Test
    void an_invalid_probe_referer_is_ignored_while_the_probe_is_off() {
        BunnyNetProperties properties = valid();
        properties.setDiagnosticProbe(false);
        properties.setDiagnosticProbeReferer("not a url");

        assertThatCode(properties::requireRealCdnConfiguration).doesNotThrowAnyException();
    }

    @Test
    void the_probe_referer_accessor_is_stripped_and_none_when_blank() {
        BunnyNetProperties properties = new BunnyNetProperties();
        assertThat(properties.probeReferer()).isNull();

        properties.setDiagnosticProbeReferer("  http://localhost:8080/  ");
        assertThat(properties.probeReferer()).isEqualTo("http://localhost:8080/");

        properties.setDiagnosticProbeReferer(null);
        assertThat(properties.probeReferer()).isNull();
    }

    @Test
    void pull_zone_host_is_the_bare_host_without_scheme_or_trailing_slash() {
        BunnyNetProperties properties = valid();
        properties.setPullZoneHostname("HTTPS://vz-example.b-cdn.net/");

        assertThat(properties.pullZoneHost()).isEqualTo("vz-example.b-cdn.net");
    }

    @Test
    void setters_accept_blank_without_throwing() {
        BunnyNetProperties properties = new BunnyNetProperties();

        properties.setPullZoneHostname("");
        properties.setVideoLibraryId("");

        assertThat(properties.getPullZoneHostname()).isEmpty();
        assertThat(properties.getVideoLibraryId()).isEmpty();
    }

    @Test
    void complete_configuration_passes_the_real_adapter_check() {
        assertThatCode(() -> valid().requireRealCdnConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void default_properties_fail_the_real_adapter_check_listing_every_problem() {
        assertThatThrownBy(() -> new BunnyNetProperties().requireRealCdnConfiguration())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.security-key")
            .hasMessageContaining("app.cdn.bunny-net.pull-zone-hostname")
            .hasMessageContaining("app.cdn.bunny-net.video-library-id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blank_security_key_fails_the_real_adapter_check(String key) {
        BunnyNetProperties properties = valid();
        properties.setSecurityKey(key);

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.security-key")
            .hasMessageNotContaining("pull-zone-hostname")
            .hasMessageNotContaining("video-library-id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://invalid-missing-config.local", "invalid-missing-config", "https://", "/",
        "vz-example.b-cdn.net/some/path", "vz example.b-cdn.net"})
    void placeholder_or_malformed_host_fails_the_real_adapter_check(String host) {
        BunnyNetProperties properties = valid();
        properties.setPullZoneHostname(host);

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.pull-zone-hostname")
            .hasMessageNotContaining("security-key");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"missing-config", "invalid-missing-config"})
    void placeholder_library_fails_the_real_adapter_check(String libraryId) {
        BunnyNetProperties properties = valid();
        properties.setVideoLibraryId(libraryId);

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.video-library-id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "a/b.mp4"})
    void blank_or_nested_file_name_fails_the_real_adapter_check(String fileName) {
        BunnyNetProperties properties = valid();
        properties.setFileName(fileName);

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.file-name");
    }

    @Test
    void the_failure_message_never_contains_the_configured_values() {
        BunnyNetProperties properties = valid();
        properties.setVideoLibraryId("missing-config");
        properties.setPullZoneHostname("secret-host-marker/path");

        assertThatThrownBy(properties::requireRealCdnConfiguration)
            .hasMessageNotContaining("test-security-key")
            .hasMessageNotContaining("secret-host-marker");
    }

    @Test
    void pull_zone_base_url_accepts_host_with_or_without_scheme_and_trailing_slash() {
        for (String host : new String[] {"vz-example.b-cdn.net", "https://vz-example.b-cdn.net/",
            "http://vz-example.b-cdn.net"}) {
            BunnyNetProperties properties = valid();
            properties.setPullZoneHostname(host);

            assertThat(properties.pullZoneBaseUrl()).isEqualTo("https://vz-example.b-cdn.net");
        }
    }

    @Test
    void to_string_never_renders_the_security_key() {
        assertThat(valid().toString())
            .doesNotContain("test-security-key")
            .contains("vz-example.b-cdn.net");
    }
}
