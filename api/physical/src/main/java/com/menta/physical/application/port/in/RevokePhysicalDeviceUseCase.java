package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceView;
import java.util.UUID;

/** Revokes an {@code ACTIVE} device, terminally (#44, US-PHYSICAL-007, design C4, D5). */
public interface RevokePhysicalDeviceUseCase {

    /**
     * Revokes the device permanently and returns its updated view.
     *
     * <p>{@code REVOKED} is terminal: revoking again fails with {@code
     * DeviceAlreadyRevokedException}, and an unknown id with {@code DeviceNotFoundException}.
     * Records a {@code DEVICE_REVOKED} audit row.
     */
    PhysicalDeviceView revoke(String deviceId, UUID actorId);
}
