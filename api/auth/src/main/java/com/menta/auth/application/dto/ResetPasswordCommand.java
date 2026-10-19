package com.menta.auth.application.dto;

/**
 * Reset-password use case input (US-AUTH-006).
 *
 * <p>{@code newPassword} is plaintext — it is validated and encoded inside the
 * use case, never persisted or logged as-is.</p>
 */
public record ResetPasswordCommand(String rawToken, String newPassword, String clientFingerprint) {
}
