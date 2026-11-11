package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the administrator's account creation page ({@code /gestion/usuarios/nuevo}).
 *
 * <p>The constraints mirror {@code CreateUserRequest} of the Auth API (email valid and at most 255
 * characters, names required and at most 100) so the administrator gets Spanish field errors without a round
 * trip. The API stays authoritative. The role travels as text and must be one of
 * {@link UserRole#staffRoles()}: {@code STUDENT}, an unknown value or none is refused with one message. This
 * is personal data of a third person: it is never logged.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateUserForm {

    @NotBlank(message = "Ingresá el email")
    @Email(message = "El formato del email es inválido")
    @Size(max = 255, message = "El email no puede superar los 255 caracteres")
    private String email;

    @NotBlank(message = "Ingresá el nombre")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String firstName;

    @NotBlank(message = "Ingresá el apellido")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String lastName;

    private String role;

    /**
     * Checks that the role is one an administrator can create. A single check covers a missing, blank,
     * unknown and {@code STUDENT} role, so the field never shows two messages.
     *
     * @return true when the role is a staff role
     */
    @AssertTrue(message = "Elegí un rol de la lista")
    public boolean isRoleAllowed() {
        return UserRole.staffRoles().stream().anyMatch(allowed -> allowed.name().equals(role));
    }

    /**
     * Returns the chosen role as an enum.
     *
     * @return the role, only meaningful once the form validated
     */
    public UserRole parsedRole() {
        return UserRole.valueOf(role);
    }

    /**
     * Stores the email without surrounding whitespace, which a paste often adds.
     *
     * @param email the submitted email, possibly null
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    /**
     * Stores the first name without surrounding whitespace.
     *
     * @param firstName the submitted first name, possibly null
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName == null ? null : firstName.strip();
    }

    /**
     * Stores the last name without surrounding whitespace.
     *
     * @param lastName the submitted last name, possibly null
     */
    public void setLastName(String lastName) {
        this.lastName = lastName == null ? null : lastName.strip();
    }
}
