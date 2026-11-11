package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.exception.WeakPasswordException;
import com.menta.auth.domain.model.PasswordPolicyViolation;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The wording of a password policy rejection, shared by every endpoint that accepts a new password so they all
 * tell the user the same thing. The domain states what failed; this class owns how it is said.
 */
final class PasswordPolicyMessages {

    private static final Map<PasswordPolicyViolation, String> VIOLATION_MESSAGES = new EnumMap<>(
        Map.of(
            PasswordPolicyViolation.TOO_SHORT, "al menos 8 caracteres",
            PasswordPolicyViolation.MISSING_UPPERCASE, "al menos una mayúscula",
            PasswordPolicyViolation.MISSING_DIGIT, "al menos un número"
        )
    );

    private PasswordPolicyMessages() {
    }

    /** Lists every unmet rule and never the rejected password. */
    static String detailOf(WeakPasswordException exception) {
        String violations = exception.getViolations().stream()
            .map(VIOLATION_MESSAGES::get)
            .collect(Collectors.joining(", "));
        return "La contraseña no cumple los requisitos: " + violations + ".";
    }
}
