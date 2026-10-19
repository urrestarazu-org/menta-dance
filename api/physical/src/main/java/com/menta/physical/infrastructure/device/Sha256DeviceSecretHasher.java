package com.menta.physical.infrastructure.device;

import com.menta.physical.application.port.out.DeviceSecretHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 digest persisted for a device secret (#44, US-PHYSICAL-007, D1).
 *
 * <p>Replicates {@code :api:auth}'s {@code Sha256Hex.hash}/{@code Sha256ActivationTokenHasher}
 * body (JDK {@link MessageDigest} + {@link HexFormat} only) -- deliberately NOT imported from
 * {@code com.menta.auth.*} (D3), so the existing module-boundary ArchUnit rule holds unchanged.</p>
 */
public final class Sha256DeviceSecretHasher implements DeviceSecretHasher {

    private static final String SHA_256 = "SHA-256";

    @Override
    public String hash(String rawSecret) {
        if (rawSecret == null) {
            throw new IllegalArgumentException("rawSecret cannot be null");
        }
        byte[] digest = messageDigest().digest(rawSecret.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory algorithm on every JDK implementation (JCA spec) -- this
            // path is unreachable in practice, mirrored from Sha256Hex/GuaranteedAlgorithm.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
