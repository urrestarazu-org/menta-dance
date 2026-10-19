package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Web-only request shape for a non-enumerating forgot-password request (US-AUTH-005). */
public record ForgotPasswordRequest(
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email es inválido")
    String email
) {
}
