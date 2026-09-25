package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceAuditJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceAuditJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the device audit trail (#44, US-PHYSICAL-007, design C4), mirroring {@code
 * VirtualCourseAuditRepositoryAdapter}'s shape. Wired to the {@code PhysicalDeviceAuditRepository}
 * out-port in P2 -- that port does not exist yet in this slice.
 */
@Component
public class PhysicalDeviceAuditRepositoryAdapter {

    private final PhysicalDeviceAuditJpaRepository auditRepository;

    public PhysicalDeviceAuditRepositoryAdapter(PhysicalDeviceAuditJpaRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void append(UUID deviceId, UUID actorId, String action, String previousValue, String newValue) {
        auditRepository.save(new PhysicalDeviceAuditJpaEntity(
            UUID.randomUUID(), deviceId, actorId, action, previousValue, newValue, Instant.now()
        ));
    }
}
