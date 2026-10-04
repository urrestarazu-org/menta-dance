package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import java.util.UUID;

/** Registers a new device, returning the raw secret exactly once (#44, US-PHYSICAL-007, design C4). */
public interface RegisterPhysicalDeviceUseCase {

    /**
     * Registers a new {@code ACTIVE} device and records the audit row.
     *
     * <p>The result carries the raw secret, available only in this response; afterwards only its
     * hash exists. {@code actorId} identifies the administrator for the audit trail.
     */
    PhysicalDeviceSecretResult register(RegisterPhysicalDeviceCommand command, UUID actorId);
}
