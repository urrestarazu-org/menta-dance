package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Web-only request shape for account activation. */
public record ActivateAccountRequest(
    @NotBlank(message = "El token de activación es obligatorio")
    String token
) {
}
