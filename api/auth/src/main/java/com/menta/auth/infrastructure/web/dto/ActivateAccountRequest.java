package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Web-only request shape for account activation.
 *
 * <p>{@code password} is the one the holder chooses. It is required only for accounts an administrator created, so
 * it is optional here: the use case rejects its absence with a typed error where it applies and ignores it
 * elsewhere. Its strength is judged by the domain, not by bean validation.
 */
public record ActivateAccountRequest(
    @NotBlank(message = "El token de activación es obligatorio")
    String token,
    String password
) {

    /** Never prints the single-use token or the chosen password. */
    @Override
    public String toString() {
        return "ActivateAccountRequest[credentials=***]";
    }
}
