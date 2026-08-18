package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Schema/seeding repository — see {@link PhysicalCapacityHoldJpaEntity}'s Javadoc. */
public interface PhysicalCapacityHoldJpaRepository extends JpaRepository<PhysicalCapacityHoldJpaEntity, UUID> {
}
