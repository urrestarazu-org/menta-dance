package com.menta.physical.application.port.out;

/** Generates a raw device secret (#44, US-PHYSICAL-007, design C2). */
public interface DeviceSecretGenerator {

    /**
     * Generates a new raw device secret.
     *
     * <p>The raw value is shown to the operator once and never persisted; only its hash, produced
     * by {@link DeviceSecretHasher}, is stored.
     */
    String generate();
}
