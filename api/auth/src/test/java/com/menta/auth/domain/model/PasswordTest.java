package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The password policy lives in one value object so no use case can bypass it
 * (US-AUTH-006). Registration and reset both construct through here, which is
 * what keeps "you may register a password you could never reset to" from
 * happening.
 */
class PasswordTest {

    @Nested
    @DisplayName("Política")
    class Policy {

        @Test
        void accepts_a_password_meeting_every_rule() {
            assertThatCode(() -> Password.of("SecurePass1")).doesNotThrowAnyException();
        }

        @Test
        void rejects_a_password_shorter_than_eight_characters() {
            assertThatThrownBy(() -> Password.of("Short1"))
                .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void rejects_a_password_without_an_uppercase_letter() {
            assertThatThrownBy(() -> Password.of("securepass1"))
                .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void rejects_a_password_without_a_digit() {
            assertThatThrownBy(() -> Password.of("SecurePassword"))
                .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void rejects_null_and_blank() {
            assertThatThrownBy(() -> Password.of(null)).isInstanceOf(WeakPasswordException.class);
            assertThatThrownBy(() -> Password.of("   ")).isInstanceOf(WeakPasswordException.class);
        }

        @Test
        void accepts_exactly_eight_characters() {
            // Boundary: the rule is "at least 8", not "more than 8".
            assertThatCode(() -> Password.of("Secure1a")).doesNotThrowAnyException();
        }

        @Test
        void does_not_trim_the_value() {
            // A leading or trailing space is a legitimate character in a
            // password. Trimming it silently would let a user set a secret they
            // could never type back identically.
            assertThat(Password.of(" Secure1a ").value()).isEqualTo(" Secure1a ");
        }
    }

    @Nested
    @DisplayName("Reporte de incumplimientos")
    class Violations {

        @Test
        void reports_every_unmet_rule_at_once() {
            // US-AUTH-006 escenario 5: "el mensaje debe detallar qué requisitos
            // no se cumplen". Reportar de a uno obligaría al usuario a
            // descubrir la política por prueba y error.
            assertThatThrownBy(() -> Password.of("abc"))
                .isInstanceOf(WeakPasswordException.class)
                .satisfies(error -> assertThat(((WeakPasswordException) error).getViolations())
                    .containsExactlyInAnyOrder(
                        PasswordPolicyViolation.TOO_SHORT,
                        PasswordPolicyViolation.MISSING_UPPERCASE,
                        PasswordPolicyViolation.MISSING_DIGIT
                    ));
        }

        @Test
        void reports_only_the_rule_that_failed() {
            assertThatThrownBy(() -> Password.of("securepass1"))
                .isInstanceOf(WeakPasswordException.class)
                .satisfies(error -> assertThat(((WeakPasswordException) error).getViolations())
                    .containsExactly(PasswordPolicyViolation.MISSING_UPPERCASE));
        }
    }

    @Nested
    @DisplayName("Custodia del valor")
    class Custody {

        @Test
        void never_exposes_the_secret_through_toString() {
            // Value objects end up in log lines, exception messages and debugger
            // output. A default toString here would leak the plaintext password
            // into all three.
            assertThat(Password.of("SecurePass1").toString()).doesNotContain("SecurePass1");
        }

        @Test
        void two_passwords_with_the_same_value_are_equal() {
            assertThat(Password.of("SecurePass1")).isEqualTo(Password.of("SecurePass1"));
        }

        @Test
        void differing_passwords_are_not_equal() {
            assertThat(Password.of("SecurePass1")).isNotEqualTo(Password.of("SecurePass2"));
        }

        @Test
        void is_equal_to_itself_and_not_equal_to_null_or_a_different_type() {
            Password password = Password.of("SecurePass1");

            assertThat(password).isEqualTo(password);
            assertThat(password).isNotEqualTo(null);
            assertThat(password).isNotEqualTo("SecurePass1");
        }

        @Test
        void equal_passwords_share_a_hash_code() {
            assertThat(Password.of("SecurePass1")).hasSameHashCodeAs(Password.of("SecurePass1"));
        }
    }
}
