package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over the {@code physical_devices} registry table.
 *
 * <p>Used only through {@code PhysicalDeviceRepositoryAdapter}; it never leaves the infrastructure
 * layer.
 */
public interface PhysicalDeviceJpaRepository extends JpaRepository<PhysicalDeviceJpaEntity, UUID> {

    /** Fleet listing order (design C3): created_at ASC, id ASC. */
    List<PhysicalDeviceJpaEntity> findAllByOrderByCreatedAtAscIdAsc();
}
