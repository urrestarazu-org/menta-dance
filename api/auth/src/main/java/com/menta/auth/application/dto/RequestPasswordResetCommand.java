package com.menta.auth.application.dto;

/**
 * Forgot-password use case input (US-AUTH-005).
 *
 * <p>{@code clientFingerprint} is an opaque, non-reversible identifier of the
 * requesting client, mirroring {@link ResendActivationCommand}.</p>
 */
public record RequestPasswordResetCommand(String email, String clientFingerprint) {
}
