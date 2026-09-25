package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.domain.model.PhysicalDevice;

final class PhysicalDeviceResultMapper {

    private PhysicalDeviceResultMapper() {
    }

    static PhysicalDeviceView toView(PhysicalDevice device) {
        return new PhysicalDeviceView(
            device.getId().getValue(),
            device.getName(),
            device.getLocation(),
            device.getStatus(),
            device.getExpiresAt(),
            device.getCreatedAt(),
            device.getUpdatedAt()
        );
    }
}
