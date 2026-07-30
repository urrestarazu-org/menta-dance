package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.TokenHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

/**
 * SHA-256 hex digest adapter for the TokenHasher port (ADR-0025).
 *
 * MySQL stores the lowercase hex digest of the raw refresh UUID; the raw
 * token never lands in storage. The algorithm is FIPS-compatible — no
 * per-tenant secret so far (open question in design.md tracks the eventual
 * HMAC variant if we add a per-tenant secret).
 *
 * Implementation notes:
 *   - {@link MessageDigest} instantiation is wrapped because SHA-256 is a
 *     mandatory JRE algorithm. We translate NoSuchAlgorithmException to
 *     IllegalStateException at construction time so a broken JVM cannot
 *     silently produce wrong data.
 *   - The digest is rebuilt per call — there is no shared mutable state.
 *     MessageDigest is NOT thread-safe; do not cache a single instance.
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final String SHA_256 = "SHA-256";

    public Sha256TokenHasher() {
        // Compile-time check: JRE MUST provide SHA-256.
        try {
            MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "JRE is missing " + SHA_256 + "; cannot hash refresh tokens", e);
        }
    }

    @Override
    public String hash(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new IllegalArgumentException("rawRefreshToken cannot be null");
        }
        return sha256Hex(rawRefreshToken);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256)
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Already checked at construction; reach here only if the JRE changes
            // mid-execution, which is unrecoverable.
            throw new IllegalStateException("SHA-256 disappeared mid-execution", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
