package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import java.time.LocalDate;

/**
 * Command to register a new user.
 * Immutable DTO for application layer.
 *
 * <p>{@code phone} must already be normalized to E.164 and {@code birthDate} is optional (may be {@code null});
 * both, and the names, are validated by the domain when the use case builds the user profile.</p>
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
    String firstName,
    String lastName,
    String phone,
    LocalDate birthDate,
    String clientFingerprint
) {

    /**
     * Never prints the password, the email, the personal data or the client fingerprint, so a stray log line cannot
     * leak them.
     */
    @Override
    public String toString() {
        return "RegisterUserCommand{role=" + role + ", credentials=***, profile=***}";
    }
}
