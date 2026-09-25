package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceView;
import java.util.UUID;

/** Revokes an {@code ACTIVE} device, terminally (#44, US-PHYSICAL-007, design C4, D5). */
public interface RevokePhysicalDeviceUseCase {

    PhysicalDeviceView revoke(String deviceId, UUID actorId);
}
