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
 * The annotated text must be an ISO {@code yyyy-MM-dd} date strictly before today.
 *
 * <p>A null or blank value is valid: the field is optional.
 * </p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PastIsoDate.Validator.class)
public @interface PastIsoDate {

    /**
     * Message shown when the date is in the future or today.
     *
     * @return the message
     */
    String message() default "La fecha de nacimiento tiene que ser anterior a hoy";

    /**
     * Message shown when the text is not an ISO date.
     *
     * @return the message
     */
    String malformedMessage() default "Ingresá una fecha de nacimiento válida";

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

    /** Validator for {@link PastIsoDate}. */
    class Validator implements ConstraintValidator<PastIsoDate, String> {

        private String malformedMessage;

        @Override
        public void initialize(PastIsoDate annotation) {
            this.malformedMessage = annotation.malformedMessage();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(value);
            } catch (DateTimeException e) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(malformedMessage).addConstraintViolation();
                return false;
            }
            return date.isBefore(LocalDate.now());
        }
    }
}
