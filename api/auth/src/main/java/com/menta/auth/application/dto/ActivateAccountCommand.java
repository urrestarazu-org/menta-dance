package com.menta.auth.application.dto;

/**
 * Activate-account use case input.
 * Pure DTO — carried from the HTTP layer to the application boundary.
 *
 * <p>{@code password} is the plaintext the holder chose. It is required only when the account was created by an
 * administrator and is ignored otherwise; {@code null} when the request carried none. {@link #toString()} never
 * prints the token or the password.</p>
 */
public record ActivateAccountCommand(String rawToken, String password) {

    @Override
    public String toString() {
        return "ActivateAccountCommand{credentials=***}";
    }
}
