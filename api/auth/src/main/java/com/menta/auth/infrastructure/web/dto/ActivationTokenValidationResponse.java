package com.menta.auth.infrastructure.web.dto;

/**
 * Body of {@code GET /api/v1/auth/activate/{token}} when the activation screen must ask for a password.
 * A token whose account needs none is answered with an empty {@code 204} instead, as before.
 */
public record ActivationTokenValidationResponse(boolean passwordRequired) {
}
