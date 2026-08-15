package com.menta.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stateless SHA-256 utility for opaque credentials owned by the auth module.
 *
 * <p>A new {@link MessageDigest} is created for every invocation because it is mutable and not
 * thread-safe. The returned digest is always lowercase hexadecimal.</p>
 */
public final class Sha256Hex {

    private static final String SHA_256 = "SHA-256";

    private Sha256Hex() {
    }

    public static void verifyAlgorithmAvailable() {
        try {
            MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE is missing " + SHA_256, exception);
        }
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256)
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE is missing " + SHA_256, exception);
        }
    }
}
