package com.menta.physical.application.port.out;

/** Hashes a raw device secret into its persisted form (#44, US-PHYSICAL-007, design C2). */
public interface DeviceSecretHasher {

    String hash(String rawSecret);
}
