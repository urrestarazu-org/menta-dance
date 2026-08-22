package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionCourseJpaRepository extends JpaRepository<SubscriptionCourseJpaEntity, Long> {

    List<SubscriptionCourseJpaEntity> findBySubscriptionId(UUID subscriptionId);

    void deleteBySubscriptionId(UUID subscriptionId);
}
