package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalDeviceJpaRepository extends JpaRepository<PhysicalDeviceJpaEntity, UUID> {

    /** Fleet listing order (design C3): created_at ASC, id ASC. */
    List<PhysicalDeviceJpaEntity> findAllByOrderByCreatedAtAscIdAsc();
}
