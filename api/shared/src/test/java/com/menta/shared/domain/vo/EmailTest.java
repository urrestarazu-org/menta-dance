package com.menta.shared.domain.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void shouldCreateValidEmail() {
        // Given
        String emailValue = "user@example.com";

        // When
        Email email = Email.of(emailValue);

        // Then
        assertNotNull(email);
        assertEquals(emailValue, email.getValue());
    }

    @Test
    void shouldNormalizeEmailToLowercase() {
        // Given
        String emailValue = "User@Example.COM";

        // When
        Email email = Email.of(emailValue);

        // Then
        assertEquals("user@example.com", email.getValue());
    }

    @Test
    void shouldTrimEmail() {
        // Given
        String emailValue = "  user@example.com  ";

        // When
        Email email = Email.of(emailValue);

        // Then
        assertEquals("user@example.com", email.getValue());
    }

    @Test
    void shouldThrowExceptionForNullEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.of(null));
    }

    @Test
    void shouldThrowExceptionForEmptyEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.of(""));
    }

    @Test
    void shouldThrowExceptionForBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("   "));
    }

    @Test
    void shouldThrowExceptionForInvalidEmailFormat() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("invalid"));
        assertThrows(IllegalArgumentException.class, () -> Email.of("invalid@"));
        assertThrows(IllegalArgumentException.class, () -> Email.of("@example.com"));
        assertThrows(IllegalArgumentException.class, () -> Email.of("invalid@.com"));
    }

    @Test
    void shouldBeEqualForSameValue() {
        Email email1 = Email.of("user@example.com");
        Email email2 = Email.of("user@example.com");

        assertEquals(email1, email2);
        assertEquals(email1.hashCode(), email2.hashCode());
    }

    @Test
    void shouldBeEqualToItself() {
        Email email = Email.of("user@example.com");

        assertEquals(email, email);
    }

    @Test
    void shouldNotBeEqualToNullOrAnotherType() {
        Email email = Email.of("user@example.com");

        assertNotEquals(null, email);
        assertNotEquals("user@example.com", email);
    }

    @Test
    void shouldExposeValueThroughToString() {
        Email email = Email.of("user@example.com");

        assertEquals("user@example.com", email.toString());
    }
}
