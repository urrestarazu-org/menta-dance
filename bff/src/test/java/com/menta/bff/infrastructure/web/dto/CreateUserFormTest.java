package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Validation of {@link CreateUserForm}. All values are fake. */
@DisplayName("CreateUserForm")
class CreateUserFormTest {

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

    private static CreateUserForm validForm() {
        CreateUserForm form = new CreateUserForm();
        form.setEmail("recepcion@example.com");
        form.setFirstName("Ada");
        form.setLastName("Lovelace");
        form.setRole("RECEPTIONIST");
        return form;
    }

    private static List<String> messagesFor(CreateUserForm form, String property) {
        Set<ConstraintViolation<CreateUserForm>> violations = validator.validateProperty(form, property);
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }

    @ParameterizedTest(name = "{0} is a valid role")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "ADMIN"})
    @DisplayName("a complete form with a staff role is valid")
    void staffRolesAreValid(String role) {
        CreateUserForm form = validForm();
        form.setRole(role);

        assertThat(validator.validate(form)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank email asks for it")
    void blankEmail(String value) {
        CreateUserForm form = validForm();
        form.setEmail(value);

        assertThat(messagesFor(form, "email")).containsExactly("Ingresá el email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "dos@@example.com", "con espacio@example.com"})
    @DisplayName("a malformed email is refused")
    void malformedEmail(String value) {
        CreateUserForm form = validForm();
        form.setEmail(value);

        assertThat(messagesFor(form, "email")).containsExactly("El formato del email es inválido");
    }

    @Test
    @DisplayName("the email is limited to 255 characters, counting the 255th as valid")
    void emailLength() {
        // 64 + 1 + 190 characters: a local part and domain labels of the longest legal size.
        String domain = "b".repeat(63) + "." + "b".repeat(63) + "." + "b".repeat(62);
        CreateUserForm form = validForm();
        form.setEmail("a".repeat(64) + "@" + domain);
        assertThat(messagesFor(form, "email")).isEmpty();

        form.setEmail("a".repeat(64) + "@" + domain + "b");
        assertThat(messagesFor(form, "email")).containsExactly("El email no puede superar los 255 caracteres");
    }

    @Test
    @DisplayName("the email and the names are stored without surrounding whitespace")
    void stripsWhitespace() {
        CreateUserForm form = new CreateUserForm();
        form.setEmail("  a@example.com ");
        form.setFirstName(" Ada ");
        form.setLastName("\tLovelace\n");

        assertThat(form.getEmail()).isEqualTo("a@example.com");
        assertThat(form.getFirstName()).isEqualTo("Ada");
        assertThat(form.getLastName()).isEqualTo("Lovelace");

        form.setEmail(null);
        form.setFirstName(null);
        form.setLastName(null);
        assertThat(form.getEmail()).isNull();
        assertThat(form.getFirstName()).isNull();
        assertThat(form.getLastName()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank first name or last name asks for it")
    void blankNames(String value) {
        CreateUserForm form = validForm();
        form.setFirstName(value);
        form.setLastName(value);

        assertThat(messagesFor(form, "firstName")).containsExactly("Ingresá el nombre");
        assertThat(messagesFor(form, "lastName")).containsExactly("Ingresá el apellido");
    }

    @Test
    @DisplayName("each name is limited to 100 characters, counting the 100th as valid")
    void nameLength() {
        CreateUserForm form = validForm();
        form.setFirstName("n".repeat(100));
        form.setLastName("a".repeat(100));
        assertThat(messagesFor(form, "firstName")).isEmpty();
        assertThat(messagesFor(form, "lastName")).isEmpty();

        form.setFirstName("n".repeat(101));
        form.setLastName("a".repeat(101));
        assertThat(messagesFor(form, "firstName")).containsExactly("El nombre no puede superar los 100 caracteres");
        assertThat(messagesFor(form, "lastName")).containsExactly("El apellido no puede superar los 100 caracteres");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"STUDENT", "WIZARD", "admin", " ADMIN"})
    @DisplayName("a missing role, STUDENT or one outside the list is refused with a single message")
    void invalidRole(String value) {
        CreateUserForm form = validForm();
        form.setRole(value);

        assertThat(messagesFor(form, "roleAllowed")).containsExactly("Elegí un rol de la lista");
    }
}
