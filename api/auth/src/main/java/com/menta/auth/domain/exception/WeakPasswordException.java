package com.menta.auth.domain.exception;

import com.menta.auth.domain.model.PasswordPolicyViolation;
import com.menta.shared.domain.exceptions.BusinessException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Thrown when a proposed password does not meet the policy (US-AUTH-006).
 *
 * <p>Carries every unmet rule, not just the first one: reporting them one at a
 * time would force the user to discover the policy by trial and error.</p>
 */
public class WeakPasswordException extends BusinessException {

    private static final String ERROR_CODE = "WEAK_PASSWORD";

    private final Set<PasswordPolicyViolation> violations;

    public WeakPasswordException(Set<PasswordPolicyViolation> violations) {
        super(ERROR_CODE, "The password does not meet the required policy");
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("violations cannot be null or empty");
        }
        this.violations = Collections.unmodifiableSet(EnumSet.copyOf(violations));
    }

    /**
     * @return the unmet rules. Never contains the attempted password — an
     *     exception message is exactly the kind of value that ends up in logs.
     */
    public Set<PasswordPolicyViolation> getViolations() {
        return violations;
    }
}
