package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    Optional<SubscriptionJpaEntity> findByPaymentId(UUID paymentId);

    Optional<SubscriptionJpaEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Resolves the user's slot through the column the unique index sits on — never a status scan. */
    Optional<SubscriptionJpaEntity> findByActiveUserId(UUID activeUserId);

    List<SubscriptionJpaEntity> findAllByUserId(UUID userId);

    /**
     * Strictly {@code ACTIVE} filter — {@code findByActiveUserId} also matches {@code PENDING}
     * through the {@code active_user_id} projection, which cannot serve self-service
     * cancellation (US-BILLING-011).
     */
    Optional<SubscriptionJpaEntity> findByUserIdAndStatus(UUID userId, String status);

    /**
     * The overlap-notice lookup (US-BILLING-011 D3): latest {@code CANCELLED} row for the same
     * user and plan whose {@code endDate} is still in the future. Served by the existing {@code
     * idx_billing_subscriptions_user_status (user_id, status)} index — no new index (A8).
     */
    Optional<SubscriptionJpaEntity> findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc(
        UUID userId, UUID planId, String status, Instant endDateAfter
    );
}
