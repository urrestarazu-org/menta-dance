package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import java.util.UUID;

/**
 * Rotates an {@code ACTIVE} device's secret, returning the new raw secret exactly once (#44,
 * US-PHYSICAL-007, design C4).
 */
public interface RotatePhysicalDeviceSecretUseCase {

    PhysicalDeviceSecretResult rotate(String deviceId, UUID actorId);
}
