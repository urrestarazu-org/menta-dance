package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceView;

/**
 * Reads one device's metadata (#44, US-PHYSICAL-007, design C4). No {@code actorId}: authorization
 * is entirely the {@code SecurityConfig} matcher's job, and there is no per-resource ownership
 * concept for a device.
 */
public interface GetPhysicalDeviceUseCase {

    PhysicalDeviceView get(String deviceId);
}
