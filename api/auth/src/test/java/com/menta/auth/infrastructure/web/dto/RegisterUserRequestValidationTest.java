package com.menta.auth.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterUserRequestValidationTest {

    private static final String PHONE = "+5491100000000";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static RegisterUserRequest request(String firstName, String lastName, String phone, LocalDate birthDate) {
        return new RegisterUserRequest(
            "student@example.com", "SecurePass123!", Role.STUDENT, firstName, lastName, phone, birthDate
        );
    }

    private static Set<String> invalidProperties(RegisterUserRequest request) {
        return validator.validate(request).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    @Test
    void accepts_a_complete_request() {
        assertThat(validator.validate(request("Ada", "Lovelace", PHONE, LocalDate.of(1990, 5, 17)))).isEmpty();
    }

    @Test
    void accepts_a_request_without_birth_date() {
        assertThat(validator.validate(request("Ada", "Lovelace", PHONE, null))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejects_a_blank_first_name(String firstName) {
        assertThat(invalidProperties(request(firstName, "Lovelace", PHONE, null))).containsExactly("firstName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejects_a_blank_last_name(String lastName) {
        assertThat(invalidProperties(request("Ada", lastName, PHONE, null))).containsExactly("lastName");
    }

    @Test
    void accepts_names_of_exactly_one_hundred_characters() {
        String name = "a".repeat(100);

        assertThat(validator.validate(request(name, name, PHONE, null))).isEmpty();
    }

    @Test
    void rejects_names_over_one_hundred_characters() {
        String tooLong = "a".repeat(101);

        assertThat(invalidProperties(request(tooLong, tooLong, PHONE, null)))
            .containsExactlyInAnyOrder("firstName", "lastName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "5491100000000",
        "+54 9 11 0000 0000",
        "+54-911-0000-0000",
        "+(54)91100000000",
        "+54abc1100000",
        "+1234567",
        "+1234567890123456",
        "+0123456789",
        " +5491100000000",
        "+5491100000000\n"
    })
    void rejects_a_phone_that_is_not_e164(String phone) {
        assertThat(invalidProperties(request("Ada", "Lovelace", phone, null))).containsExactly("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+12345678", "+5491100000000", "+123456789012345"})
    void accepts_phones_in_e164_format(String phone) {
        assertThat(validator.validate(request("Ada", "Lovelace", phone, null))).isEmpty();
    }

    @Test
    void rejects_a_birth_date_of_today_or_later() {
        assertThat(invalidProperties(request("Ada", "Lovelace", PHONE, LocalDate.now())))
            .containsExactly("birthDate");
        assertThat(invalidProperties(request("Ada", "Lovelace", PHONE, LocalDate.now().plusDays(1))))
            .containsExactly("birthDate");
    }

    @Test
    void accepts_a_birth_date_in_the_past() {
        assertThat(validator.validate(request("Ada", "Lovelace", PHONE, LocalDate.now().minusDays(1)))).isEmpty();
    }
}
