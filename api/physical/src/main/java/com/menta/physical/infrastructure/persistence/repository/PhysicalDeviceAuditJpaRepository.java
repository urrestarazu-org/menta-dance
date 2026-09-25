package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceAuditJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalDeviceAuditJpaRepository extends JpaRepository<PhysicalDeviceAuditJpaEntity, UUID> {

    List<PhysicalDeviceAuditJpaEntity> findByDeviceIdOrderByCreatedAtAsc(UUID deviceId);
}
