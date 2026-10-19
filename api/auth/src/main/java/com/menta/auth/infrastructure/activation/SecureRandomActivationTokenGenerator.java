package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/** Generates opaque 256-bit activation credentials with a CSPRNG. */
public final class SecureRandomActivationTokenGenerator implements ActivationTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public SecureRandomActivationTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomActivationTokenGenerator(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom cannot be null");
        }
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
