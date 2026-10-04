package com.menta.bff.infrastructure.integration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Builds JWT-shaped access tokens like the ones the Auth API issues.
 *
 * <p>The BFF reads the {@code role} claim at login and never verifies the
 * signature, so a fixed signature segment is enough.</p>
 */
public final class TestAccessTokens {

    private TestAccessTokens() {
    }

    /**
     * Returns a compact JWT whose payload is the given JSON.
     *
     * @param payloadJson raw JSON payload
     * @return header.payload.signature token
     */
    public static String withPayload(String payloadJson) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
            + "." + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
            + ".signature";
    }

    /**
     * Returns a compact JWT carrying the given role claim.
     *
     * @param role role name, e.g. {@code STUDENT}
     * @return header.payload.signature token
     */
    public static String withRole(String role) {
        return withPayload("{\"sub\":\"1\",\"email\":\"user@example.com\",\"role\":\"" + role + "\"}");
    }
}
