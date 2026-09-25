package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.domain.model.DeviceStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Secret-less device metadata (#44, US-PHYSICAL-007, design C2/C6). Returned by {@code get},
 * {@code revoke}, and wrapped by {@code list}. No component name contains {@code secret} or
 * {@code hash} — a dedicated reflective test asserts this type-level fact (C10).
 */
public record PhysicalDeviceResponse(
    UUID id,
    String name,
    String location,
    DeviceStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {

    public static PhysicalDeviceResponse from(PhysicalDeviceView view) {
        return new PhysicalDeviceResponse(
            view.id(), view.name(), view.location(), view.status(),
            view.expiresAt(), view.createdAt(), view.updatedAt()
        );
    }
}
