package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validation of the personal data on {@link RegisterForm}. All values are fake.
 */
@DisplayName("RegisterForm personal data")
class RegisterFormTest {

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

    private static RegisterForm validForm() {
        RegisterForm form = new RegisterForm();
        form.setEmail("ana@example.com");
        form.setPassword("Secreta123");
        form.setPasswordConfirmation("Secreta123");
        form.setFirstName("Ana");
        form.setLastName("Prueba");
        form.setPhone("+54 9 11 0000-0000");
        return form;
    }

    private static List<String> messagesFor(RegisterForm form, String property) {
        Set<ConstraintViolation<RegisterForm>> violations = validator.validateProperty(form, property);
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("a form with all required data and no birth date is valid")
    void validFormHasNoViolations() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank first name asks for it")
    void blankFirstName(String value) {
        RegisterForm form = validForm();
        form.setFirstName(value);

        assertThat(messagesFor(form, "firstName")).containsExactly("Ingresá tu nombre");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank last name asks for it")
    void blankLastName(String value) {
        RegisterForm form = validForm();
        form.setLastName(value);

        assertThat(messagesFor(form, "lastName")).containsExactly("Ingresá tu apellido");
    }

    @Test
    @DisplayName("names are trimmed and 100 characters is the limit")
    void nameLengthLimit() {
        RegisterForm form = validForm();
        form.setFirstName("  " + "a".repeat(100) + "  ");
        form.setLastName("b".repeat(101));

        assertThat(form.getFirstName()).hasSize(100);
        assertThat(messagesFor(form, "firstName")).isEmpty();
        assertThat(messagesFor(form, "lastName"))
                .containsExactly("El apellido no puede superar los 100 caracteres");
        form.setFirstName("a".repeat(101));
        assertThat(messagesFor(form, "firstName"))
                .containsExactly("El nombre no puede superar los 100 caracteres");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank phone asks for it, and only for it")
    void blankPhone(String value) {
        RegisterForm form = validForm();
        form.setPhone(value);

        assertThat(messagesFor(form, "phone")).containsExactly("Ingresá tu teléfono");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "+54 9 11 0000-0000|+5491100000000",
        "+54 (9) 11 0000.0000|+5491100000000",
        "  +1 (555) 000-0000  |+15550000000",
        "+12345678|+12345678",
        "+123456789012345|+123456789012345"})
    @DisplayName("separators are stripped and the normalized number is what gets sent")
    void normalizesSeparators(String typed, String expected) {
        RegisterForm form = validForm();
        form.setPhone(typed);

        assertThat(messagesFor(form, "phone")).isEmpty();
        assertThat(form.normalizedPhone()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "5491100000000", "011 0000 0000", "+0491100000000", "+54911abc0000", "+1234567",
        "+1234567890123456", "+", " - ( ) ", "++5491100000000", "+54 9 11 0000 0000 ext 5"})
    @DisplayName("a phone that is not E.164 once normalized is rejected with a Spanish hint")
    void rejectsNonE164(String typed) {
        RegisterForm form = validForm();
        form.setPhone(typed);

        assertThat(messagesFor(form, "phone"))
                .containsExactly("Ingresá el teléfono con código de país, por ejemplo +54 9 11 0000 0000");
    }

    @Test
    @DisplayName("the phone keeps what the person typed")
    void phoneKeepsTypedValue() {
        RegisterForm form = validForm();
        form.setPhone("+54 9 11 0000-0000");

        assertThat(form.getPhone()).isEqualTo("+54 9 11 0000-0000");
    }

    @Test
    @DisplayName("the birth date is optional")
    void birthDateIsOptional() {
        RegisterForm form = validForm();
        form.setBirthDate("");

        assertThat(messagesFor(form, "birthDate")).isEmpty();
        assertThat(form.parsedBirthDate()).isNull();
        form.setBirthDate(null);
        assertThat(form.parsedBirthDate()).isNull();
    }

    @Test
    @DisplayName("a past birth date is accepted and parsed as ISO yyyy-MM-dd")
    void pastBirthDate() {
        RegisterForm form = validForm();
        form.setBirthDate("1990-05-17");

        assertThat(messagesFor(form, "birthDate")).isEmpty();
        assertThat(form.parsedBirthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    @DisplayName("yesterday is accepted, today and tomorrow are rejected")
    void birthDateMustBeStrictlyInThePast() {
        final String message = "La fecha de nacimiento tiene que ser anterior a hoy";
        RegisterForm form = validForm();
        form.setBirthDate(LocalDate.now().minusDays(1).toString());
        assertThat(messagesFor(form, "birthDate")).isEmpty();
        form.setBirthDate(LocalDate.now().toString());
        assertThat(messagesFor(form, "birthDate")).containsExactly(message);
        form.setBirthDate(LocalDate.now().plusDays(1).toString());
        assertThat(messagesFor(form, "birthDate")).containsExactly(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"17/05/1990", "1990-5-17", "1990-02-30", "mañana", "1990-05-17T10:00"})
    @DisplayName("a malformed birth date is rejected with a Spanish message")
    void malformedBirthDate(String value) {
        RegisterForm form = validForm();
        form.setBirthDate(value);

        assertThat(messagesFor(form, "birthDate")).containsExactly("Ingresá una fecha de nacimiento válida");
        assertThat(form.parsedBirthDate()).isNull();
    }
}
