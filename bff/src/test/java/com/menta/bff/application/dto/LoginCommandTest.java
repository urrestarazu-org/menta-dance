package com.menta.bff.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for LoginCommand DTO.
 */
class LoginCommandTest {

    private static final String CLIENT_ADDRESS = "203.0.113.9";

    @Test
    void shouldCreateLoginCommand() {
        LoginCommand command = new LoginCommand("user@example.com", "secret123", CLIENT_ADDRESS);

        assertThat(command.email()).isEqualTo("user@example.com");
        assertThat(command.password()).isEqualTo("secret123");
    }

    @Test
    void shouldRejectNullEmail() {
        assertThatThrownBy(() -> new LoginCommand(null, "password", CLIENT_ADDRESS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email");
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> new LoginCommand("user@example.com", null, CLIENT_ADDRESS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("password");
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> new LoginCommand("", "password", CLIENT_ADDRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email cannot be blank");
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> new LoginCommand("user@example.com", "", CLIENT_ADDRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password cannot be blank");
    }

    @Test
    void toStringShouldNotLeakPassword() {
        LoginCommand command = new LoginCommand("user@example.com", "secret123", CLIENT_ADDRESS);

        String toString = command.toString();

        assertThat(toString).doesNotContain("secret123");
        assertThat(toString).contains("user@example.com");
        assertThat(toString).contains("***");
    }
}
