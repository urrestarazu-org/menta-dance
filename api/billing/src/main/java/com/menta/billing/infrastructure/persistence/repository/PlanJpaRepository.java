package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, UUID> {

    @Query("SELECT p FROM PlanJpaEntity p WHERE p.status = :status ORDER BY p.price ASC")
    List<PlanJpaEntity> findAllByStatusOrderByPriceAsc(@Param("status") PlanStatus status);

    Optional<PlanJpaEntity> findByIdAndStatus(UUID id, PlanStatus status);
}
