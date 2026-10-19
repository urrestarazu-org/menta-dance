package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import java.util.UUID;

/**
 * Rotates an {@code ACTIVE} device's secret, returning the new raw secret exactly once (#44,
 * US-PHYSICAL-007, design C4).
 */
public interface RotatePhysicalDeviceSecretUseCase {

    /**
     * Replaces the device's secret hash and returns the new raw secret once.
     *
     * <p>Fails with {@code DeviceNotFoundException} for an unknown id and with {@code
     * DeviceRevokedException} for a revoked device. Records a {@code DEVICE_SECRET_ROTATED} audit
     * row holding only hash prefixes.
     */
    PhysicalDeviceSecretResult rotate(String deviceId, UUID actorId);
}
