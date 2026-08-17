package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Web-only request shape for consuming a reset token (US-AUTH-006).
 *
 * <p>{@code newPassword} carries no length/pattern validation here — the
 * password policy is owned exclusively by the domain via {@code Password.of()},
 * mirroring {@link RegisterUserRequest}.</p>
 */
public record ResetPasswordRequest(
    @NotBlank(message = "El token es obligatorio")
    String token,

    @NotBlank(message = "La nueva contraseña es obligatoria")
    String newPassword
) {
}
