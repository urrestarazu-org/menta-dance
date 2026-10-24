package com.menta.bff.infrastructure.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated text, once its separators are stripped, must be a strict E.164 phone number.
 *
 * <p>A null or blank value is valid here: requiring it is the job of {@code @NotBlank}, so the person
 * sees one message at a time.
 * </p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = E164Phone.Validator.class)
public @interface E164Phone {

    /**
     * Message shown when the number is not valid.
     *
     * @return the message
     */
    String message() default "Ingresá el teléfono con código de país, por ejemplo +54 9 11 0000 0000";

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

    /** Validator for {@link E164Phone}. */
    class Validator implements ConstraintValidator<E164Phone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            return PhoneNumbers.isE164(PhoneNumbers.normalize(value));
        }
    }
}
