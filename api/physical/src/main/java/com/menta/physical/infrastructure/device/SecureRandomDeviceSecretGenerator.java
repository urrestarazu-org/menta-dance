package com.menta.physical.infrastructure.device;

import com.menta.physical.application.port.out.DeviceSecretGenerator;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque 256-bit device secrets with a CSPRNG (#44, US-PHYSICAL-007, D1).
 *
 * <p>Replicates {@code :api:auth}'s {@code SecureRandomActivationTokenGenerator} shape using
 * JDK {@link SecureRandom} only -- deliberately NOT imported from {@code com.menta.auth.*} (D3),
 * so the existing module-boundary ArchUnit rule holds unchanged.</p>
 */
public final class SecureRandomDeviceSecretGenerator implements DeviceSecretGenerator {

    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom;

    public SecureRandomDeviceSecretGenerator() {
        this(new SecureRandom());
    }

    SecureRandomDeviceSecretGenerator(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom cannot be null");
        }
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
