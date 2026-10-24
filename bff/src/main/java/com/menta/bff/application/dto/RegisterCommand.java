package com.menta.bff.application.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Command DTO for the public account registration.
 *
 * <p>The web registration only ever creates students, so instances come from
 * {@link #student(String, String, String, String, String, LocalDate, String)}: the form has no
 * role field and nothing the browser sends can change the role.
 * </p>
 *
 * <p>SECURITY: Never log the password nor the personal data (names, phone, birth date).
 * </p>
 *
 * @param email         Email address of the new account
 * @param password      Plain-text password (sent over HTTPS, never stored by the BFF)
 * @param firstName     Given name, already trimmed
 * @param lastName      Family name, already trimmed
 * @param phone         Phone number already normalized to E.164 (for example {@code +5491100000000})
 * @param birthDate     Date of birth in the past; nullable because it is optional
 * @param role          Role requested from the Auth API
 * @param clientAddress Canonical originating address of the client (ADR-0035).
 *     Nullable: when it cannot be established the Auth API falls back to the
 *     peer address it observes.
 */
public record RegisterCommand(
        String email,
        String password,
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        String role,
        String clientAddress) {

    private static final String STUDENT_ROLE = "STUDENT";

    /**
     * Compact constructor with validation.
     */
    public RegisterCommand {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        Objects.requireNonNull(firstName, "firstName cannot be null");
        Objects.requireNonNull(lastName, "lastName cannot be null");
        Objects.requireNonNull(phone, "phone cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password cannot be blank");
        }
        if (firstName.isBlank()) {
            throw new IllegalArgumentException("firstName cannot be blank");
        }
        if (lastName.isBlank()) {
            throw new IllegalArgumentException("lastName cannot be blank");
        }
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone cannot be blank");
        }
    }

    /**
     * Creates the registration of a student account.
     *
     * @param email         Email address of the new account
     * @param password      Plain-text password
     * @param firstName     Given name
     * @param lastName      Family name
     * @param phone         Phone number in E.164 format
     * @param birthDate     Date of birth, may be null
     * @param clientAddress Canonical originating client address, may be null
     * @return the command with the STUDENT role
     */
    public static RegisterCommand student(String email, String password, String firstName, String lastName,
            String phone, LocalDate birthDate, String clientAddress) {
        return new RegisterCommand(email, password, firstName, lastName, phone, birthDate, STUDENT_ROLE,
                clientAddress);
    }

    /**
     * Custom toString that prevents password and personal data leakage in logs.
     *
     * @return String representation without password or personal data
     */
    @Override
    public String toString() {
        return "RegisterCommand[email=" + email + ", password=***, firstName=***, lastName=***, phone=***,"
                + " birthDate=***, role=" + role + ", clientAddress=" + clientAddress + "]";
    }
}
