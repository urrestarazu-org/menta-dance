package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;

/** Manual mapper JPA entity ↔ domain -- no MapStruct (unused in this project, see #96). */
public final class PhysicalDeviceJpaMapper {

    private PhysicalDeviceJpaMapper() {
    }

    public static PhysicalDevice toDomain(PhysicalDeviceJpaEntity entity) {
        return new PhysicalDevice(
            DeviceId.of(entity.getId()),
            entity.getName(),
            entity.getLocation(),
            entity.getSecretHash(),
            DeviceStatus.valueOf(entity.getStatus()),
            entity.getExpiresAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static PhysicalDeviceJpaEntity toEntity(PhysicalDevice device) {
        return new PhysicalDeviceJpaEntity(
            device.getId().getValue(),
            device.getName(),
            device.getLocation(),
            device.getSecretHash(),
            device.getStatus().name(),
            device.getExpiresAt(),
            device.getCreatedAt(),
            device.getUpdatedAt()
        );
    }
}
