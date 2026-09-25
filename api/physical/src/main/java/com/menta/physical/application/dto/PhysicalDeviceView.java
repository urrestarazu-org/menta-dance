package com.menta.physical.application.dto;

import com.menta.physical.domain.model.DeviceStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Secret-less device metadata (#44, US-PHYSICAL-007, design C2). Returned by {@code get}, {@code
 * list}, and {@code revoke}. No component name contains {@code secret} or {@code hash} — a
 * dedicated reflective test asserts this type-level fact so a future edit cannot regress it by
 * omission.
 */
public record PhysicalDeviceView(
    UUID id,
    String name,
    String location,
    DeviceStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
}
