package com.menta.physical.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code CheckInRequest} (#45, US-PHYSICAL-008) is a discriminated union
 * closed by two {@code @AssertTrue} predicates (design C1): a QR body must
 * carry the reader triple and never {@code studentId}; a MANUAL body must
 * carry {@code studentId} and never a QR-only field.
 */
class CheckInRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static String randomStudentId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void a_well_formed_qr_request_has_no_violations() {
        CheckInRequest request = new CheckInRequest("QR", "qr:payload", "reader-1", "secret", null);

        Set<ConstraintViolation<CheckInRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void a_well_formed_manual_request_has_no_violations() {
        CheckInRequest request =
            new CheckInRequest("MANUAL", null, null, null, randomStudentId());

        Set<ConstraintViolation<CheckInRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "qr:payload"})
    void a_manual_request_carrying_qr_credentials_is_rejected(String qrCredentials) {
        CheckInRequest request =
            new CheckInRequest("MANUAL", qrCredentials, null, null, randomStudentId());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "reader-1"})
    void a_manual_request_carrying_device_id_is_rejected(String deviceId) {
        CheckInRequest request =
            new CheckInRequest("MANUAL", null, deviceId, null, randomStudentId());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "secret"})
    void a_manual_request_carrying_device_token_is_rejected(String deviceToken) {
        CheckInRequest request =
            new CheckInRequest("MANUAL", null, null, deviceToken, randomStudentId());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void a_manual_request_without_a_student_id_is_rejected(String studentId) {
        CheckInRequest request = new CheckInRequest("MANUAL", null, null, null, studentId);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void a_qr_request_carrying_a_student_id_is_rejected() {
        CheckInRequest request =
            new CheckInRequest("QR", "qr:payload", "reader-1", "secret", randomStudentId());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void a_qr_request_missing_qr_credentials_is_rejected(String qrCredentials) {
        CheckInRequest request = new CheckInRequest("QR", qrCredentials, "reader-1", "secret", null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void a_qr_request_missing_device_id_is_rejected(String deviceId) {
        CheckInRequest request = new CheckInRequest("QR", "qr:payload", deviceId, "secret", null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void a_qr_request_missing_device_token_is_rejected(String deviceToken) {
        CheckInRequest request = new CheckInRequest("QR", "qr:payload", "reader-1", deviceToken, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "qr", "OTHER"})
    void an_unrecognized_type_is_rejected(String type) {
        CheckInRequest request = new CheckInRequest(type, "qr:payload", "reader-1", "secret", null);

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
