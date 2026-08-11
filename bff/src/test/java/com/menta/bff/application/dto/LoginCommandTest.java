package com.menta.bff.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for LoginCommand DTO.
 */
class LoginCommandTest {

    @Test
    void shouldCreateLoginCommand() {
        LoginCommand command = new LoginCommand("user@example.com", "secret123");

        assertThat(command.email()).isEqualTo("user@example.com");
        assertThat(command.password()).isEqualTo("secret123");
    }

    @Test
    void shouldRejectNullEmail() {
        assertThatThrownBy(() -> new LoginCommand(null, "password"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email");
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> new LoginCommand("user@example.com", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("password");
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> new LoginCommand("", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email cannot be blank");
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> new LoginCommand("user@example.com", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password cannot be blank");
    }

    @Test
    void toStringShouldNotLeakPassword() {
        LoginCommand command = new LoginCommand("user@example.com", "secret123");

        String toString = command.toString();

        assertThat(toString).doesNotContain("secret123");
        assertThat(toString).contains("user@example.com");
        assertThat(toString).contains("***");
    }
}
