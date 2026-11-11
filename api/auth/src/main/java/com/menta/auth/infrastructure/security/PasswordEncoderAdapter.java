package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing PasswordEncoderPort.
 * Infrastructure layer - bridges application and Spring Security.
 */
@Component
public class PasswordEncoderAdapter implements PasswordEncoderPort {

    /** Entropy of the discarded secret behind an unusable hash. */
    private static final int UNUSABLE_SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordEncoder passwordEncoder;

    public PasswordEncoderAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Hashes 32 bytes from {@link SecureRandom}, URL-safe Base64 encoded (43 characters, within bcrypt's 72-byte
     * input limit), and returns only the hash: the secret goes out of scope here, so nobody ever knows it.
     */
    @Override
    public String encodeUnusable() {
        byte[] secret = new byte[UNUSABLE_SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
