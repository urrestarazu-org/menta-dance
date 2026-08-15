package com.menta.auth.application.port.out;

/**
 * Cross-module port to encrypt/decrypt the activation delivery payload so it
 * can be held durably in {@code auth_activation_tokens.delivery_ciphertext}
 * without ever persisting the raw token in clear (auth-account-activation
 * design: "Dispatch del outbox").
 */
public interface ActivationDeliveryCipher {

    /**
     * Encrypts the plaintext (recipient + raw token material) under the
     * current {@code AUTH_ACTIVATION_DELIVERY_KEY}.
     */
    DeliveryEnvelope encrypt(String plaintext);

    /**
     * Decrypts a previously-issued envelope. MUST select the key by
     * {@link DeliveryEnvelope#getKeyVersion()}; the key is never versioned
     * into the envelope itself.
     */
    String decrypt(DeliveryEnvelope envelope);
}
