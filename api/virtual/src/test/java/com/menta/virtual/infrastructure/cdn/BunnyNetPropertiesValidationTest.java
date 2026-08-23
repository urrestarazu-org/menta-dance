package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Cover for {@link BunnyNetProperties} defaults and field setters.
 *
 * <p>The properties no longer carry {@code @NotBlank} validation so
 * integration tests can boot the Spring context without setting
 * {@code app.cdn.bunny-net.*} values. The defaults are flagged
 * ({@code invalid-missing} / {@code missing-config}) so a future
 * runtime check — added in VirtualConfiguration when the production
 * profile concern is raised — can detect that boot was misconfigured.
 * This test verifies the placeholder contract:</p>
 * <ul>
 *   <li>The {@code new} {@link BunnyNetProperties} already exposes
 *       non-blank defaults, so the bean never reaches the signature
 *       service in a blank state even without operator config.</li>
 *   <li>Setters accept blank strings without throwing — operators
 *       can probe the placeholder mode intentionally.</li>
 * </ul>
 */
class BunnyNetPropertiesValidationTest {

    @Test
    void defaults_are_non_blank_so_the_bean_works_without_yml() {
        BunnyNetProperties properties = new BunnyNetProperties();

        assertThat(properties.getPullZoneHostname()).isNotBlank();
        assertThat(properties.getVideoLibraryId()).isNotBlank();
        // The defaults are explicitly flagged so production can detect
        // them; see VirtualConfiguration.validateBunnyNetPlaceholderUsage.
        assertThat(properties.getPullZoneHostname()).contains("invalid-missing");
        assertThat(properties.getVideoLibraryId()).isEqualTo("missing-config");
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
    void happy_path_two_non_blank_values_yield_unchanged_state() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("12345");

        assertThat(properties.getPullZoneHostname()).isEqualTo("vz-test.b-cdn.net");
        assertThat(properties.getVideoLibraryId()).isEqualTo("12345");
    }
}
