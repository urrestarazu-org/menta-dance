package com.menta.auth.application.dto;

/**
 * Uniform resend-activation outcome. Deliberately carries no fields so its
 * shape cannot reveal whether the target email existed, was already active,
 * or was pending (auth-account-activation design decision #6: "Respuesta
 * uniforme para registro/reenvío").
 */
public record ResendActivationResult() {

    public static final ResendActivationResult ACKNOWLEDGED = new ResendActivationResult();
}
