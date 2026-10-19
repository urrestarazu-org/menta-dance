package com.menta.auth.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.domain.model.PasswordPolicyViolation;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeakPasswordExceptionTest {

    @Test
    void carries_the_given_violations() {
        Set<PasswordPolicyViolation> violations = EnumSet.of(PasswordPolicyViolation.TOO_SHORT);

        WeakPasswordException exception = new WeakPasswordException(violations);

        assertThat(exception.getViolations()).containsExactly(PasswordPolicyViolation.TOO_SHORT);
    }

    @Test
    void rejects_a_null_violations_set() {
        assertThatThrownBy(() -> new WeakPasswordException(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("violations");
    }

    @Test
    void rejects_an_empty_violations_set() {
        assertThatThrownBy(() -> new WeakPasswordException(EnumSet.noneOf(PasswordPolicyViolation.class)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("violations");
    }
}
