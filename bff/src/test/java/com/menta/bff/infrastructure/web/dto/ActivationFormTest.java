package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validation of the password the holder of an admin-created account chooses on {@link ActivationForm}.
 * The policy is the one shared with {@link RegisterForm} and {@link ResetPasswordForm}.
 */
@DisplayName("ActivationForm")
class ActivationFormTest {

    private static final String PASSWORD = "Secreta123";

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

    private static ActivationForm form(String password, String confirmation) {
        ActivationForm form = new ActivationForm();
        form.setToken("activation-token-abc");
        form.setPassword(password);
        form.setPasswordConfirmation(confirmation);
        return form;
    }

    private static List<String> messages(ActivationForm form) {
        return validator.validate(form).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("a policy-compliant password that is repeated is valid")
    void validPasswordHasNoViolations() {
        assertThat(messages(form(PASSWORD, PASSWORD))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank password asks for one")
    void blankPassword(String value) {
        assertThat(messages(form(value, value))).contains("Ingresá una contraseña");
    }

    @ParameterizedTest
    @CsvSource({
        "Ab1, La contraseña debe tener al menos 8 caracteres",
        "secreta123, La contraseña debe incluir al menos una mayúscula",
        "SecretaAbc, La contraseña debe incluir al menos un número"
    })
    @DisplayName("a password that breaks one rule of the policy says which one")
    void weakPassword(String password, String message) {
        assertThat(messages(form(password, password))).containsExactly(message);
    }

    @Test
    @DisplayName("a confirmation that differs is rejected on the confirmation, not on the password")
    void confirmationMustMatch() {
        ActivationForm form = form(PASSWORD, "Otra12345");

        assertThat(validator.validate(form)).singleElement().satisfies(violation -> {
            assertThat(violation.getMessage()).isEqualTo("Las contraseñas no coinciden");
            assertThat(violation.getPropertyPath()).hasToString("passwordConfirmed");
        });
    }

    @Test
    @DisplayName("clearing the passwords empties both fields and keeps the token")
    void clearPasswordsKeepsTheToken() {
        ActivationForm form = form(PASSWORD, PASSWORD);

        form.clearPasswords();

        assertThat(form.getPassword()).isNull();
        assertThat(form.getPasswordConfirmation()).isNull();
        assertThat(form.getToken()).isEqualTo("activation-token-abc");
    }

    @Test
    @DisplayName("toString carries neither the passwords nor the token")
    void toStringHasNoSecrets() {
        assertThat(form(PASSWORD, PASSWORD).toString())
                .doesNotContain(PASSWORD).doesNotContain("activation-token-abc");
    }
}
