package com.menta.bff.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterCommand")
class RegisterCommandTest {

    private static final String PHONE = "+5491100000000";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);

    private static RegisterCommand student(String email, String password) {
        return RegisterCommand.student(email, password, "Ana", "Prueba", PHONE, BIRTH_DATE, null);
    }

    @Test
    @DisplayName("student() always carries the STUDENT role and the personal data")
    void studentFactoryFixesTheRole() {
        RegisterCommand command = RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", "Prueba", PHONE, BIRTH_DATE, "203.0.113.9");

        assertThat(command.role()).isEqualTo("STUDENT");
        assertThat(command.email()).isEqualTo("ana@example.com");
        assertThat(command.firstName()).isEqualTo("Ana");
        assertThat(command.lastName()).isEqualTo("Prueba");
        assertThat(command.phone()).isEqualTo(PHONE);
        assertThat(command.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(command.clientAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("the birth date is optional")
    void birthDateIsOptional() {
        RegisterCommand command =
                RegisterCommand.student("ana@example.com", "Secreta123", "Ana", "Prueba", PHONE, null, null);

        assertThat(command.birthDate()).isNull();
    }

    @Test
    @DisplayName("toString never renders the password nor the personal data")
    void toStringHidesSensitiveData() {
        String text = student("ana@example.com", "Secreta123").toString();

        assertThat(text).contains("ana@example.com").contains("STUDENT")
                .doesNotContain("Secreta123")
                .doesNotContain("Ana")
                .doesNotContain("Prueba")
                .doesNotContain(PHONE)
                .doesNotContain("1990");
    }

    @Test
    @DisplayName("rejects a blank email or password")
    void rejectsBlankCredentials() {
        assertThatThrownBy(() -> student(" ", "Secreta123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> student("ana@example.com", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a missing or blank first name, last name or phone")
    void rejectsMissingPersonalData() {
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", null, "Prueba", PHONE, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", null, PHONE, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", "Prueba", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", " ", "Prueba", PHONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", " ", PHONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", "Prueba", " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
