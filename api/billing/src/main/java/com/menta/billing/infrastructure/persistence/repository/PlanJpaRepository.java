package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code billing_plans}.
 *
 * <p>Used by {@code PlanRepositoryAdapter} and by the E2E billing fixture.
 */
public interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, UUID> {

    /** Lists the plans in the given status, cheapest first. */
    @Query("SELECT p FROM PlanJpaEntity p WHERE p.status = :status ORDER BY p.price ASC")
    List<PlanJpaEntity> findAllByStatusOrderByPriceAsc(@Param("status") PlanStatus status);

    /** Finds a plan by id only when it is in the given status; it backs the active-only lookup. */
    Optional<PlanJpaEntity> findByIdAndStatus(UUID id, PlanStatus status);
}
