package com.menta.auth.application.port.out;

/**
 * Port for password encoding operations.
 * Abstraction for password hashing infrastructure.
 */
public interface PasswordEncoderPort {

    /**
     * Encodes a raw password into the form that is persisted.
     *
     * <p>The result is not meant to be compared by string equality; use {@link #matches} to verify a password.
     *
     * @param rawPassword the plaintext password
     * @return the encoded form, never the plaintext
     */
    String encode(String rawPassword);

    /**
     * Verifies a raw password against a previously encoded one.
     *
     * @param rawPassword the plaintext password presented by the caller
     * @param encodedPassword the stored value produced by {@link #encode}
     * @return true when the raw password corresponds to the encoded one
     */
    boolean matches(String rawPassword, String encodedPassword);
}
