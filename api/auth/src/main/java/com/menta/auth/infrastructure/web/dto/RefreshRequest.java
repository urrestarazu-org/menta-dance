package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh endpoint request body. The raw refresh token opaque string lives
 * here; the controller hands it to RefreshUseCase which hashes server-side.
 */
public record RefreshRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
