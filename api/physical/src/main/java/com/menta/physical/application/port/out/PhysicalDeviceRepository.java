package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalDevice;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link PhysicalDevice} (#44, US-PHYSICAL-007, design C3). */
public interface PhysicalDeviceRepository {

    /** Inserts a new device or updates an existing one, matched by {@link PhysicalDevice#getId()}. */
    PhysicalDevice save(PhysicalDevice device);

    Optional<PhysicalDevice> findById(DeviceId deviceId);

    /**
     * Ordered {@code created_at ASC, id ASC} (design C3). Unpaginated on purpose: a device fleet
     * is a handful of door readers.
     */
    List<PhysicalDevice> findAll();
}
