package com.menta.virtual.infrastructure.cdn.local;

import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Deterministic, credential-free {@link BunnyNetSignatureService} for local
 * development and E2E acceptance testing (profile {@code e2e-bunny-net},
 * issue #129). Preserves the same
 * {@code <pullZoneHostname>/<videoLibraryId>/<videoId>} shape as {@link
 * com.menta.virtual.infrastructure.cdn.StringFormatBunnyNetSignatureService}
 * and additionally appends real {@code exp}/{@code sig} query parameters, so
 * a Bruno journey can assert determinism against this adapter without ever
 * reaching Bunny.net.
 *
 * <p>{@code sig = SHA-256("menta-local-e2e|" + videoLibraryId + "|" +
 * videoId + "|" + exp)}, rendered as 64 lowercase hex characters. The salt
 * {@code "menta-local-e2e"} is a PUBLIC constant, not a secret: this digest
 * carries no credential and is useless against the real CDN. ADR-0040
 * records this decision and the fail-closed profile guard that keeps this
 * bean out of production-like environments
 * ({@code com.menta.virtual.infrastructure.config.VirtualConfiguration}).</p>
 */
public final class LocalBunnyNetSignatureService implements BunnyNetSignatureService {

    private static final String SALT = "menta-local-e2e";

    private final BunnyNetProperties properties;

    public LocalBunnyNetSignatureService(BunnyNetProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateSignedUrl(String videoId, long expirationTimeInSeconds) {
        String signature = sign(properties.getVideoLibraryId(), videoId, expirationTimeInSeconds);
        return String.format(
            Locale.ROOT,
            "%s/%s/%s?exp=%d&sig=%s",
            properties.getPullZoneHostname(),
            properties.getVideoLibraryId(),
            videoId,
            expirationTimeInSeconds,
            signature
        );
    }

    private static String sign(String videoLibraryId, String videoId, long expiration) {
        String payload = SALT + "|" + videoLibraryId + "|" + videoId + "|" + expiration;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm on every JVM implementation (JLS/JCA spec).
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }
}
