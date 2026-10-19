package com.menta.auth.application.dto;

/**
 * Uniform forgot-password outcome. Deliberately carries no fields so its
 * shape cannot reveal whether the target email existed or was active
 * (US-AUTH-005 escenario 2/5: never reveal account existence or state).
 */
public record RequestPasswordResetResult() {

    public static final RequestPasswordResetResult ACKNOWLEDGED = new RequestPasswordResetResult();
}
