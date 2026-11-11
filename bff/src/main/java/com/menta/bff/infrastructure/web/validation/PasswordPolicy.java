package com.menta.bff.infrastructure.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated password must meet the policy of the Auth API's {@code Password} type (US-AUTH-006): at least
 * 8 characters, one uppercase letter and one digit, counted on the raw value.
 *
 * <p>It is the single place where the BFF states that policy, shared by every form that asks for a new
 * password (registration, recovery and activation), so the person gets Spanish field errors without a round
 * trip. {@code \p{javaUpperCase}} and {@code \p{javaDigit}} are the very {@code Character} predicates the API
 * uses. The API stays authoritative. Each broken rule reports its own message, so the composing constraints are
 * deliberately not collapsed into a single violation.
 * </p>
 */
@NotBlank(message = "Ingresá una contraseña")
@Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
@Pattern(regexp = "(?s).*\\p{javaUpperCase}.*", message = "La contraseña debe incluir al menos una mayúscula")
@Pattern(regexp = "(?s).*\\p{javaDigit}.*", message = "La contraseña debe incluir al menos un número")
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
public @interface PasswordPolicy {

    /**
     * Message of the constraint itself, which is never reported: the composing constraints speak for it.
     *
     * @return the message
     */
    String message() default "La contraseña no cumple los requisitos";

    /**
     * Validation groups.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
