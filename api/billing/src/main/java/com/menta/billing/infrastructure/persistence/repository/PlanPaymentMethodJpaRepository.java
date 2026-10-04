package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_plan_payment_methods}, one row per payment method a
 * plan accepts.
 *
 * <p>Used by {@code PlanRepositoryAdapter}, which reads them per plan or, for a list, in one batch
 * by plan ids, and by the E2E billing fixture.
 */
public interface PlanPaymentMethodJpaRepository extends JpaRepository<PlanPaymentMethodJpaEntity, Long> {

    /** Lists the payment-method rows of one plan. */
    List<PlanPaymentMethodJpaEntity> findByPlanId(UUID planId);

    /**
     * Lists the payment-method rows of several plans in one query, which avoids one lookup per plan
     * when listing plans.
     */
    List<PlanPaymentMethodJpaEntity> findByPlanIdIn(List<UUID> planIds);
}
