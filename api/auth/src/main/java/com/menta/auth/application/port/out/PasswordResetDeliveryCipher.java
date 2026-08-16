package com.menta.auth.application.port.out;

/**
 * Cross-module port to encrypt/decrypt the password-reset delivery payload so
 * it can be held durably without ever persisting the raw token in clear.
 * Mirrors {@link ActivationDeliveryCipher}, but deliberately a separate port —
 * and, in infrastructure, a separate key — so rotating or compromising one
 * delivery key never affects the other. Sharing a key across two independent
 * security domains would couple their blast radii for no benefit.
 */
public interface PasswordResetDeliveryCipher {

    /** Encrypts the plaintext (recipient + raw token material) under the current key. */
    DeliveryEnvelope encrypt(String plaintext);

    /**
     * Decrypts a previously-issued envelope. MUST select the key by
     * {@link DeliveryEnvelope#getKeyVersion()}; the key is never versioned
     * into the envelope itself.
     */
    String decrypt(DeliveryEnvelope envelope);
}
