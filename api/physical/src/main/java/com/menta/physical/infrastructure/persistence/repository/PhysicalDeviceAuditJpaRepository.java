package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceAuditJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over the append-only {@code physical_device_audit} table.
 *
 * <p>Used only through {@code PhysicalDeviceAuditRepositoryAdapter}; it never leaves the
 * infrastructure layer.
 */
public interface PhysicalDeviceAuditJpaRepository extends JpaRepository<PhysicalDeviceAuditJpaEntity, UUID> {

    /** Lists the audit rows of one device in chronological order, oldest first. */
    List<PhysicalDeviceAuditJpaEntity> findByDeviceIdOrderByCreatedAtAsc(UUID deviceId);
}
