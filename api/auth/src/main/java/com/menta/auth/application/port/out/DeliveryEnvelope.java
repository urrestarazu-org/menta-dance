package com.menta.auth.application.port.out;

import java.util.Arrays;
import java.util.Objects;

/**
 * Encrypted-at-rest envelope for activation delivery material, mirroring the
 * {@code delivery_ciphertext}/{@code delivery_nonce}/{@code
 * delivery_key_version} columns added by V3 (auth-account-activation
 * design: "Dispatch del outbox").
 *
 * <p>AES-GCM's standard nonce length is 12 bytes; {@code keyVersion} lets a
 * decrypting handler pick the matching key without the key ever being
 * persisted itself.</p>
 */
public final class DeliveryEnvelope {

    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final byte[] ciphertext;
    private final byte[] nonce;
    private final int keyVersion;

    private DeliveryEnvelope(byte[] ciphertext, byte[] nonce, int keyVersion) {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext cannot be null or empty");
        }
        if (nonce == null || nonce.length != GCM_NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                "nonce must be exactly " + GCM_NONCE_LENGTH_BYTES + " bytes (AES-GCM)"
            );
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        this.ciphertext = ciphertext.clone();
        this.nonce = nonce.clone();
        this.keyVersion = keyVersion;
    }

    public static DeliveryEnvelope of(byte[] ciphertext, byte[] nonce, int keyVersion) {
        return new DeliveryEnvelope(ciphertext, nonce, keyVersion);
    }

    public byte[] getCiphertext() {
        return ciphertext.clone();
    }

    public byte[] getNonce() {
        return nonce.clone();
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeliveryEnvelope that)) {
            return false;
        }
        return keyVersion == that.keyVersion
            && Arrays.equals(ciphertext, that.ciphertext)
            && Arrays.equals(nonce, that.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(ciphertext), Arrays.hashCode(nonce), keyVersion);
    }
}
