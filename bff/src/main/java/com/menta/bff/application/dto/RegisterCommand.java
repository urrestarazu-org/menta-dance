package com.menta.bff.application.dto;

import java.util.Objects;

/**
 * Command DTO for the public account registration.
 *
 * <p>The web registration only ever creates students, so instances come from
 * {@link #student(String, String, String)}: the form has no role field and
 * nothing the browser sends can change the role.
 * </p>
 *
 * <p>SECURITY: Never log the password field.
 * </p>
 *
 * @param email         Email address of the new account
 * @param password      Plain-text password (sent over HTTPS, never stored by the BFF)
 * @param role          Role requested from the Auth API
 * @param clientAddress Canonical originating address of the client (ADR-0035).
 *     Nullable: when it cannot be established the Auth API falls back to the
 *     peer address it observes.
 */
public record RegisterCommand(String email, String password, String role, String clientAddress) {

    private static final String STUDENT_ROLE = "STUDENT";

    /**
     * Compact constructor with validation.
     */
    public RegisterCommand {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password cannot be blank");
        }
    }

    /**
     * Creates the registration of a student account.
     *
     * @param email         Email address of the new account
     * @param password      Plain-text password
     * @param clientAddress Canonical originating client address, may be null
     * @return the command with the STUDENT role
     */
    public static RegisterCommand student(String email, String password, String clientAddress) {
        return new RegisterCommand(email, password, STUDENT_ROLE, clientAddress);
    }

    /**
     * Custom toString that prevents password leakage in logs.
     *
     * @return String representation without password
     */
    @Override
    public String toString() {
        return "RegisterCommand[email=" + email + ", password=***, role=" + role
                + ", clientAddress=" + clientAddress + "]";
    }
}
