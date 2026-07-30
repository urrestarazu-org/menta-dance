package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout endpoint request body. The raw refresh token opaque string is
 * validated as not-blank at the web layer; the use case hashes it
 * server-side before lookup.
 */
public record LogoutRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
