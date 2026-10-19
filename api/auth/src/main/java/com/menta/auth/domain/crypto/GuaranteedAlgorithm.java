package com.menta.auth.domain.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Isolates the one checked exception the JCA spec makes structurally
 * unreachable for algorithms every conformant JDK is required to support
 * (see {@link MessageDigest}'s javadoc for the mandatory list, which
 * includes SHA-256). Kept to exactly this one call — never add anything
 * else here — so this stays the only file the domain/application 100%
 * coverage gate needs to exclude (#96); growing a caller's real logic
 * never rides along under that exclusion.
 */
final class GuaranteedAlgorithm {

    private GuaranteedAlgorithm() {
    }

    static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE is missing " + algorithm, exception);
        }
    }
}
