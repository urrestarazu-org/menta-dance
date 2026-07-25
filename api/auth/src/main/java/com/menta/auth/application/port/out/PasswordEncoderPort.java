package com.menta.auth.application.port.out;

/**
 * Port for password encoding operations.
 * Abstraction for password hashing infrastructure.
 */
public interface PasswordEncoderPort {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
