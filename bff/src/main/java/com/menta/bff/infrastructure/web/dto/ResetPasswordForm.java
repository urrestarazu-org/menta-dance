package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.infrastructure.web.validation.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the {@code /restablecer-contrasena} page.
 *
 * <p>The password follows the {@link PasswordPolicy} shared with the other password forms, which mirrors the
 * API's {@code Password} type. The API stays authoritative. The password is never rendered back: the
 * template has no value for it.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordForm {

    private String token;

    @PasswordPolicy
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
