package com.menta.billing.infrastructure.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Derives an opaque, non-reversible rate-limit key from the requesting IP.
 *
 * <p>Deliberately simpler than auth's {@code ClientFingerprint} (#195, decision D1): this
 * endpoint's rate limit is scraping prevention on a public, low-stakes read (plan prices), not a
 * security-critical login/activation budget. Billing already sits behind the same nginx
 * reverse-proxy setup auth does, but the one real server-side caller today — the BFF's {@code
 * BillingApiAdapter} — is bounded by its own 5-minute single-flight cache (#177), so it calls this
 * endpoint at most once per 5 minutes regardless of how many fingerprints it would otherwise
 * collapse into. No live caller is under-counted in a way that matters today.</p>
 *
 * <p>If a second, non-caching server-side caller of billing's public endpoints is ever built,
 * revisit this: {@code com.menta.auth.infrastructure.web.controller.ClientFingerprint}'s
 * trusted-proxy CIDR pattern (ADR-0035 — Nginx sanitizes the header, the caller re-emits the
 * canonical origin, the API trusts it only from a gated peer) is the precedent to follow — a
 * single-file mirror of auth's fingerprint alone would not close the gap, since the BFF's call to
 * billing bypasses nginx entirely today.</p>
 */
// Explicit bean name: the simple class name "clientFingerprint" collides
// with auth's own ClientFingerprint once api:app assembles both modules
// into one Spring context (ConflictingBeanDefinitionException otherwise).
@Component("billingClientFingerprint")
final class ClientFingerprint {

    String from(HttpServletRequest request) {
        return sha256Hex(request.getRemoteAddr());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a mandatory JDK algorithm", impossible);
        }
    }
}
