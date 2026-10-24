package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.infrastructure.web.validation.E164Phone;
import com.menta.bff.infrastructure.web.validation.PastIsoDate;
import com.menta.bff.infrastructure.web.validation.PhoneNumbers;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.LocalDate;
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
 * <p>The personal data follows the API's {@code UserProfile}: first and last name are
 * required and at most 100 characters, the phone must be E.164 once the separators the
 * person typed are stripped (the form keeps what was typed so it can be shown again, and
 * {@link #normalizedPhone()} is what travels), and the optional birth date is an ISO date
 * strictly in the past. This is personal data: it is never logged.
 * </p>
 *
 * <p>There is deliberately no role property: the BFF always registers students.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterForm {

    @NotBlank(message = "Ingresá tu nombre")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String firstName;

    @NotBlank(message = "Ingresá tu apellido")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String lastName;

    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El formato del email es inválido")
    private String email;

    @NotBlank(message = "Ingresá tu teléfono")
    @E164Phone
    private String phone;

    @PastIsoDate
    private String birthDate;

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
     * Stores the first name without surrounding whitespace, as the API does.
     *
     * @param firstName the submitted first name, possibly null
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName == null ? null : firstName.strip();
    }

    /**
     * Stores the last name without surrounding whitespace, as the API does.
     *
     * @param lastName the submitted last name, possibly null
     */
    public void setLastName(String lastName) {
        this.lastName = lastName == null ? null : lastName.strip();
    }

    /**
     * Stores the birth date text, treating an empty value as not provided since the field is optional.
     *
     * @param birthDate the submitted {@code yyyy-MM-dd} text, possibly null or blank
     */
    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate == null || birthDate.isBlank() ? null : birthDate.strip();
    }

    /**
     * Returns the phone as the API expects it: separators stripped.
     *
     * @return the E.164 number, only meaningful once the form validated
     */
    public String normalizedPhone() {
        return PhoneNumbers.normalize(phone);
    }

    /**
     * Returns the birth date as a date.
     *
     * @return the parsed date, or null when it was not provided or is not a valid date
     */
    public LocalDate parsedBirthDate() {
        try {
            return birthDate == null ? null : LocalDate.parse(birthDate);
        } catch (DateTimeException e) {
            return null;
        }
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
