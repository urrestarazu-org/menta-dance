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
     * Produces the encoded form of a random secret that is generated, hashed and immediately forgotten, so that
     * no password matches it. Every call yields a different value.
     *
     * <p>It is the placeholder stored for an account whose holder has not chosen a password yet. The secret is
     * never returned, logged or persisted in the clear.
     *
     * @return an encoded value no one can authenticate with
     */
    String encodeUnusable();

    /**
     * Verifies a raw password against a previously encoded one.
     *
     * @param rawPassword the plaintext password presented by the caller
     * @param encodedPassword the stored value produced by {@link #encode}
     * @return true when the raw password corresponds to the encoded one
     */
    boolean matches(String rawPassword, String encodedPassword);
}
