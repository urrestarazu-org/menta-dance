package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validation cover for {@link BunnyNetProperties}. The orchestrator's
 * spec asks the {@code @Validated} configuration to reject a blank
 * {@code pullZoneHostname} — fail-fast at startup, no nulls handed to
 * {@link StringFormatBunnyNetSignatureService}.
 *
 * <p>The test exercises the Bean Validation pipeline directly so a
 * Spring context is not required. Two checks:</p>
 * <ul>
 *   <li>{@link #blank_pullZoneHostname_violates_NotBlank} — empty
 *       hostname is a violation.</li>
 *   <li>{@link #happy_path_two_non_blank_fields_yields_zero_violations} —
 *       the production-shaped payload binds clean.</li>
 * </ul>
 */
class BunnyNetPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void buildValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void blank_pullZoneHostname_violates_NotBlank() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("");
        properties.setVideoLibraryId("12345");

        var violations = validator.validate(properties);

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("pullZoneHostname");
    }

    @Test
    void blank_videoLibraryId_violates_NotBlank() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz.b-cdn.net");
        properties.setVideoLibraryId("");

        var violations = validator.validate(properties);

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("videoLibraryId");
    }

    @Test
    void happy_path_two_non_blank_fields_yields_zero_violations() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("12345");

        var violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }
}
