package com.menta.auth.domain.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Stateless SHA-256 utility shared by application use cases and infrastructure adapters.
 *
 * <p>Lives in the domain layer because it has no external dependencies (pure JDK crypto), so
 * both {@code application} and {@code infrastructure} can depend on it without violating the
 * Clean Architecture dependency rule. A new {@link MessageDigest} is created for every
 * invocation because it is mutable and not thread-safe. The returned digest is always lowercase
 * hexadecimal.</p>
 */
public final class Sha256Hex {

    private static final String SHA_256 = "SHA-256";

    private Sha256Hex() {
    }

    public static void verifyAlgorithmAvailable() {
        GuaranteedAlgorithm.messageDigest(SHA_256);
    }

    public static String hash(String value) {
        byte[] digest = GuaranteedAlgorithm.messageDigest(SHA_256)
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
