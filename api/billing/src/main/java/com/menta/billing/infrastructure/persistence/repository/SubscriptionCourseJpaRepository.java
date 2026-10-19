package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_subscription_courses}, the course ids frozen on a
 * subscription at activation.
 *
 * <p>Used only through {@code SubscriptionRepositoryAdapter}, which replaces the whole set of a
 * subscription when it saves it.
 */
public interface SubscriptionCourseJpaRepository extends JpaRepository<SubscriptionCourseJpaEntity, Long> {

    /** Lists the course rows stored for a subscription. */
    List<SubscriptionCourseJpaEntity> findBySubscriptionId(UUID subscriptionId);

    /**
     * Deletes every course row of a subscription.
     *
     * <p>The adapter flushes right after, so that re-inserting the same courses does not collide
     * with {@code uq_billing_subscription_courses}.
     */
    void deleteBySubscriptionId(UUID subscriptionId);
}
