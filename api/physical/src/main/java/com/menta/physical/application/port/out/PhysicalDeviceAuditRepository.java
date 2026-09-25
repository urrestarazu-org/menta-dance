package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.DeviceId;
import java.util.UUID;

/**
 * Append-only audit trail for device management operations (#44, US-PHYSICAL-007, design C4),
 * mirroring {@code VirtualCourseAuditRepository}'s shape.
 */
public interface PhysicalDeviceAuditRepository {

    /**
     * @param previousValue a {@code status=...;secretHashPrefix=<8 hex>} snapshot before the
     *     change, or {@code null} for a registration (there is no "before"). Never the raw secret
     *     or a full hash (design C4).
     * @param newValue the same snapshot shape after the change.
     */
    void append(DeviceId deviceId, UUID actorId, String action, String previousValue, String newValue);
}
