package com.menta.auth.application.dto;

/**
 * Refresh use case input. The raw token is the proof-of-possession; the
 * application hashes it before lookup.
 */
public record RefreshCommand(String rawRefreshToken) {
}
