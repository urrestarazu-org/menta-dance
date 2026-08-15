package com.menta.auth.application.dto;

/**
 * Resend-activation use case input.
 *
 * <p>{@code clientFingerprint} is an opaque, non-reversible identifier of the
 * requesting client, mirroring {@link RegisterUserCommand#clientFingerprint()}.
 * It MAY be {@code null} until the HTTP layer (task 3.2) derives it from the
 * request.</p>
 */
public record ResendActivationCommand(String email, String clientFingerprint) {
}
