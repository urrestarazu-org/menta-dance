package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;

/**
 * Command to register a new user.
 * Immutable DTO for application layer.
 *
 * <p>{@code clientFingerprint} is an opaque, non-reversible identifier of the
 * requesting client (auth-account-activation design: "Los commands de
 * aplicación no reciben HttpServletRequest; infrastructure deriva el
 * fingerprint del cliente y lo entrega como valor opaco"). It MAY be
 * {@code null} until the HTTP layer (task 3.1) derives it from the request.</p>
 */
public record RegisterUserCommand(
    String email,
    String password,
    Role role,
    String clientFingerprint
) {
}
