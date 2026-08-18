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
 * <p>Deliberately simpler than auth's {@code ClientFingerprint}: this
 * endpoint's rate limit is scraping prevention on a public, low-stakes read
 * (plan prices), not a security-critical login/activation budget, so the
 * trusted-proxy/X-Forwarded-For handling that budget needs is not worth its
 * complexity here. If billing ever sits behind the same reverse-proxy setup
 * auth does, revisit this rather than silently under-counting by IP.</p>
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
