package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;

/**
 * Returned by exactly two endpoints, register and rotate-secret (#44, US-PHYSICAL-007, design C2,
 * C6, D4). {@code secret} carries the raw value exactly once — no persisted or listed type ever
 * holds it again.
 */
public record PhysicalDeviceSecretResponse(PhysicalDeviceResponse device, String secret) {

    public static PhysicalDeviceSecretResponse from(PhysicalDeviceSecretResult result) {
        return new PhysicalDeviceSecretResponse(
            PhysicalDeviceResponse.from(result.device()), result.rawSecret()
        );
    }
}
