package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Schema/seeding repository — see {@link PhysicalCapacityAssignmentJpaEntity}'s Javadoc. */
public interface PhysicalCapacityAssignmentJpaRepository
    extends JpaRepository<PhysicalCapacityAssignmentJpaEntity, UUID> {
}
