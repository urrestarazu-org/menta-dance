package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
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
}
