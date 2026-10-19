package com.menta.auth.application.dto;

/**
 * Logout use case input. The raw token is hashed server-side to find the
 * family, then revoked atomically with a UserLoggedOut outbox event.
 */
public record LogoutCommand(String rawRefreshToken) {
}
