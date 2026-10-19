package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDeviceView;
import java.util.List;

/**
 * Lists the whole device fleet, unpaginated (#44, US-PHYSICAL-007, design C3/C4). No {@code
 * actorId}: authorization is entirely the {@code SecurityConfig} matcher's job.
 */
public interface ListPhysicalDevicesUseCase {

    /**
     * Returns every registered device, whatever its status.
     *
     * <p>Ordered by creation time and then id; unpaginated by design.
     */
    List<PhysicalDeviceView> list();
}
