package com.menta.auth.application.dto;

/**
 * Login use case input.
 * Pure DTO — carried from the HTTP layer to the application boundary.
 *
 * <p>{@code clientFingerprint} is an opaque, non-reversible identifier of the
 * requesting origin, mirroring {@link RegisterUserCommand}: application
 * commands never receive an {@code HttpServletRequest}; infrastructure derives
 * the fingerprint and hands it over as an opaque value (ADR-0035).</p>
 */
public record LoginCommand(String email, String password, String clientFingerprint) {
}
