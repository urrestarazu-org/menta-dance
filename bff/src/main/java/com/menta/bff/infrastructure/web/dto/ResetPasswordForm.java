package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the {@code /restablecer-contrasena} page.
 *
 * <p>The password constraints are the ones of {@link RegisterForm}: they mirror the API's
 * {@code Password} type (at least 8 characters, one uppercase letter, one digit). The API stays
 * authoritative. The password is never rendered back: the template has no value for it.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordForm {

    private String token;

    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(regexp = "(?s).*\\p{javaUpperCase}.*", message = "La contraseña debe incluir al menos una mayúscula")
    @Pattern(regexp = "(?s).*\\p{javaDigit}.*", message = "La contraseña debe incluir al menos un número")
    private String password;

    private String passwordConfirmation;

    /**
     * Checks that the confirmation repeats the password.
     *
     * @return true when both values are equal
     */
    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirmation);
    }
}
