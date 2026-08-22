package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPaymentMethodJpaRepository extends JpaRepository<PlanPaymentMethodJpaEntity, Long> {

    List<PlanPaymentMethodJpaEntity> findByPlanId(UUID planId);

    List<PlanPaymentMethodJpaEntity> findByPlanIdIn(List<UUID> planIds);
}
