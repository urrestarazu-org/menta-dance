package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Cover for the library-management part of {@link BunnyNetProperties}: the API key and its check (#840). */
class BunnyNetLibraryPropertiesTest {

    private static final String API_KEY = "test-library-api-key-xyz";

    private static BunnyNetProperties valid() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-example.b-cdn.net");
        properties.setVideoLibraryId("123456");
        properties.setSecurityKey("test-security-key");
        properties.setApiKey(API_KEY);
        return properties;
    }

    @Test
    void the_api_key_is_absent_by_default_and_the_management_api_defaults_are_the_documented_ones() {
        BunnyNetProperties properties = new BunnyNetProperties();

        assertThat(properties.getApiKey()).isBlank();
        assertThat(properties.getApiBaseUrl()).isEqualTo("https://video.bunnycdn.com");
        assertThat(properties.getApiConnectTimeoutMs()).isEqualTo(2000);
        assertThat(properties.getApiReadTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void a_complete_configuration_passes_the_library_check() {
        assertThatCode(valid()::requireRealLibraryConfiguration).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void a_missing_api_key_fails_the_check_naming_the_property_and_its_variable(String blank) {
        BunnyNetProperties properties = valid();
        properties.setApiKey(blank);

        assertThatThrownBy(properties::requireRealLibraryConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.api-key")
            .hasMessageContaining("APP_CDN_BUNNYNET_APIKEY");
    }

    @Test
    void the_api_key_must_not_be_the_token_signing_key_and_the_message_never_echoes_either() {
        BunnyNetProperties properties = valid();
        properties.setApiKey("same-secret-value");
        properties.setSecurityKey("same-secret-value");

        assertThatThrownBy(properties::requireRealLibraryConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.api-key")
            .hasMessageContaining("security-key")
            .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain("same-secret-value"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"missing-config", "e2e-library", "12 34", "12/34", "../1"})
    void the_library_id_must_be_numeric_because_it_is_a_path_segment_of_the_management_api(String libraryId) {
        BunnyNetProperties properties = valid();
        properties.setVideoLibraryId(libraryId);

        assertThatThrownBy(properties::requireRealLibraryConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.video-library-id")
            .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(API_KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "video.bunnycdn.com", "ftp://video.bunnycdn.com", "http://", "https://exa mple.com"})
    void the_management_api_base_url_must_be_an_absolute_http_url(String baseUrl) {
        BunnyNetProperties properties = valid();
        properties.setApiBaseUrl(baseUrl);

        assertThatThrownBy(properties::requireRealLibraryConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.cdn.bunny-net.api-base-url");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void the_timeouts_must_be_positive(int timeout) {
        BunnyNetProperties properties = valid();
        properties.setApiConnectTimeoutMs(timeout);
        properties.setApiReadTimeoutMs(timeout);

        assertThatThrownBy(properties::requireRealLibraryConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("api-connect-timeout-ms")
            .hasMessageContaining("api-read-timeout-ms");
    }

    @Test
    void the_api_key_is_not_rendered_by_to_string() {
        assertThat(valid().toString()).doesNotContain(API_KEY);
    }

    @Test
    void the_token_signing_check_does_not_require_the_api_key() {
        BunnyNetProperties properties = valid();
        properties.setApiKey("");

        assertThatCode(properties::requireRealCdnConfiguration).doesNotThrowAnyException();
    }
}
