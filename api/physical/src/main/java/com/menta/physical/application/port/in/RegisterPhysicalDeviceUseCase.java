package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import java.util.UUID;

/** Registers a new device, returning the raw secret exactly once (#44, US-PHYSICAL-007, design C4). */
public interface RegisterPhysicalDeviceUseCase {

    PhysicalDeviceSecretResult register(RegisterPhysicalDeviceCommand command, UUID actorId);
}
