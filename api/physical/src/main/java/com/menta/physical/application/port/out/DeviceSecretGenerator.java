package com.menta.physical.application.port.out;

/** Generates a raw device secret (#44, US-PHYSICAL-007, design C2). */
public interface DeviceSecretGenerator {

    String generate();
}
