package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookInboxJpaRepository extends JpaRepository<WebhookInboxJpaEntity, Long> {

    @Query(
        "SELECT w FROM WebhookInboxJpaEntity w WHERE w.status = com.menta.billing.infrastructure.webhook."
            + "WebhookInboxStatus.RECEIVED OR (w.status = com.menta.billing.infrastructure.webhook."
            + "WebhookInboxStatus.RETRY_PENDING AND w.nextAttemptAt <= :now) ORDER BY w.id ASC"
    )
    List<WebhookInboxJpaEntity> findEligibleForProcessing(@Param("now") Instant now, Pageable pageable);
}
