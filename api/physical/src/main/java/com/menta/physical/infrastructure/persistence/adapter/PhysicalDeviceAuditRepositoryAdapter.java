package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceAuditJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceAuditJpaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the device audit trail (#44, US-PHYSICAL-007, design C4), mirroring {@code
 * VirtualCourseAuditRepositoryAdapter}'s shape.
 */
@Component
@RequiredArgsConstructor
public class PhysicalDeviceAuditRepositoryAdapter implements PhysicalDeviceAuditRepository {

    private final PhysicalDeviceAuditJpaRepository auditRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(DeviceId deviceId, UUID actorId, String action, String previousValue, String newValue) {
        auditRepository.save(new PhysicalDeviceAuditJpaEntity(
            UUID.randomUUID(), deviceId.getValue(), actorId, action, previousValue, newValue, clock.now()
        ));
    }
}
