package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * #44, US-PHYSICAL-007, design C6. {@code expiresAt} is optional, inert metadata (D2) — no bean
 * validation constrains it, matching {@link com.menta.physical.application.dto.RegisterPhysicalDeviceCommand}.
 * {@code @Size} bounds mirror the {@code V23} column widths ({@code VARCHAR(120)}/{@code VARCHAR(160)}).
 */
public record RegisterPhysicalDeviceRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 160) String location,
    Instant expiresAt
) {
}
