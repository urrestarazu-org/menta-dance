package com.menta.auth.infrastructure.passwordreset;

import com.menta.auth.application.port.out.PasswordResetTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/** Generates opaque 256-bit password-reset credentials with a CSPRNG. */
public final class SecureRandomPasswordResetTokenGenerator implements PasswordResetTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureRandomPasswordResetTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomPasswordResetTokenGenerator(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom cannot be null");
        }
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // URL-safe and unpadded: the value travels inside an email link.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
