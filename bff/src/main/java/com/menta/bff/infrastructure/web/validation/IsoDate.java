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
import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * The annotated text must be a real calendar date written as ISO {@code yyyy-MM-dd}, the value a date input
 * submits. Any date is valid, past or future.
 *
 * <p>A null or blank value is valid: pair it with {@code @NotBlank} when the field is required, so each case has
 * its own message.
 * </p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoDate.Validator.class)
public @interface IsoDate {

    /**
     * Message shown when the text is not an ISO date.
     *
     * @return the message
     */
    String message() default "Ingresá una fecha válida, con el formato aaaa-mm-dd";

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

    /** Validator for {@link IsoDate}. */
    class Validator implements ConstraintValidator<IsoDate, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                LocalDate.parse(value);
                return true;
            } catch (DateTimeException e) {
                return false;
            }
        }
    }
}
