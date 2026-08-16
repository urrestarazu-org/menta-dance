package com.menta.auth.infrastructure.passwordreset;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for short-lived password-reset delivery material.
 *
 * <p>Uses a key configured independently from the activation delivery key
 * (ADR-0032 applies the same nonce/rotation policy to both). Sharing one key
 * across the two flows would mean rotating or compromising it takes both
 * down at once — two independent security domains would inherit a single
 * blast radius for no operational benefit.</p>
 */
public final class AesGcmPasswordResetDeliveryCipher implements PasswordResetDeliveryCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final int keyVersion;
    private final SecureRandom secureRandom;

    public AesGcmPasswordResetDeliveryCipher(String base64Key, int keyVersion) {
        this(base64Key, keyVersion, new SecureRandom());
    }

    AesGcmPasswordResetDeliveryCipher(String base64Key, int keyVersion, SecureRandom secureRandom) {
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom cannot be null");
        }
        byte[] decoded = decodeKey(base64Key);
        this.key = new SecretKeySpec(decoded, "AES");
        this.keyVersion = keyVersion;
        this.secureRandom = secureRandom;
    }

    @Override
    public DeliveryEnvelope encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext cannot be null or blank");
        }
        // Fresh nonce per encryption: GCM breaks outright if a nonce is ever
        // reused under the same key.
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return DeliveryEnvelope.of(
                cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce, keyVersion
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "Unable to encrypt password reset delivery payload", exception
            );
        }
    }

    @Override
    public String decrypt(DeliveryEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }
        if (envelope.getKeyVersion() != keyVersion) {
            throw new IllegalArgumentException("Unsupported password reset delivery key version");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, envelope.getNonce())
            );
            return new String(cipher.doFinal(envelope.getCiphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Invalid password reset delivery envelope", exception);
        }
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("password reset delivery key cannot be null or blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key);
            if (decoded.length != KEY_BYTES) {
                throw new IllegalArgumentException(
                    "password reset delivery key must be exactly 256 bits"
                );
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "password reset delivery key must be base64-encoded 256-bit data", exception
            );
        }
    }
}
