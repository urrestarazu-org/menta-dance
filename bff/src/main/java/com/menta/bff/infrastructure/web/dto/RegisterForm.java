package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the {@code /registro} page.
 *
 * <p>The constraints mirror what the Auth API enforces so the user gets Spanish
 * field errors without a round trip: the email rules of {@code RegisterUserRequest}
 * ({@code @NotBlank}, {@code @Email}) and the password policy of the API's
 * {@code Password} type (US-AUTH-006: at least 8 characters, one uppercase letter,
 * one digit, counted on the raw value). {@code \p{javaUpperCase}} and
 * {@code \p{javaDigit}} are the very {@code Character} predicates the API uses.
 * The API stays authoritative; its 400 is mapped to a generic form error.
 * </p>
 *
 * <p>There is deliberately no role property: the BFF always registers students.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El formato del email es inválido")
    private String email;

    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(regexp = "(?s).*\\p{javaUpperCase}.*", message = "La contraseña debe incluir al menos una mayúscula")
    @Pattern(regexp = "(?s).*\\p{javaDigit}.*", message = "La contraseña debe incluir al menos un número")
    private String password;

    private String passwordConfirmation;

    /**
     * Checks that the confirmation repeats the password. It is a form-only check: the API never sees
     * the confirmation.
     *
     * @return true when both values are equal
     */
    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirmation);
    }

    /**
     * Stores the email without surrounding whitespace, which a paste often adds.
     *
     * @param email the submitted email, possibly null
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
