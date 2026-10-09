package com.menta.bff.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterCommand")
class RegisterCommandTest {

    @Test
    @DisplayName("student() always carries the STUDENT role")
    void studentFactoryFixesTheRole() {
        RegisterCommand command = RegisterCommand.student("ana@example.com", "Secreta123", "203.0.113.9");

        assertThat(command.role()).isEqualTo("STUDENT");
        assertThat(command.email()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("toString never renders the password")
    void toStringHidesThePassword() {
        RegisterCommand command = RegisterCommand.student("ana@example.com", "Secreta123", null);

        assertThat(command.toString()).doesNotContain("Secreta123");
    }

    @Test
    @DisplayName("rejects a blank email or password")
    void rejectsBlankCredentials() {
        assertThatThrownBy(() -> RegisterCommand.student(" ", "Secreta123", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegisterCommand.student("ana@example.com", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
