package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.infrastructure.web.validation.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the {@code /activar} page.
 *
 * <p>The token travels in a hidden field. The password and its repetition exist only when the account was
 * created by an administrator and its holder must choose one: for the others the page has no such fields and
 * the form carries just the token. The password follows the {@link PasswordPolicy} shared with registration
 * and recovery, the API stays authoritative, and it is never rendered back: the template has no value for it
 * and the controller empties it before showing the page again.
 * </p>
 *
 * <p>The token and the passwords are credentials. This class deliberately has no {@code toString} that could
 * print them.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ActivationForm {

    private String token;

    @PasswordPolicy
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
     * Forgets both passwords so that the page shown again after an error carries no secret.
     */
    public void clearPasswords() {
        password = null;
        passwordConfirmation = null;
    }
}
