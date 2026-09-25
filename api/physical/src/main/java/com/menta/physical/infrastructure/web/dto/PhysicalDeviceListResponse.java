package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalDeviceView;
import java.util.List;

/**
 * Wire shape wrapping the device fleet under a {@code devices} key (#44, US-PHYSICAL-007, design
 * C6), mirroring {@link PhysicalCourseManagementListResponse}'s wrapper shape rather than a bare
 * array. Never exposes a secret or hash for a device in any status (R5).
 */
public record PhysicalDeviceListResponse(List<PhysicalDeviceResponse> devices) {

    public static PhysicalDeviceListResponse from(List<PhysicalDeviceView> views) {
        return new PhysicalDeviceListResponse(views.stream().map(PhysicalDeviceResponse::from).toList());
    }
}
