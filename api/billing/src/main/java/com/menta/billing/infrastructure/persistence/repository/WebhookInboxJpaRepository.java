package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code billing_webhook_inbox}.
 *
 * <p>Used by {@code WebhookInboxAppenderAdapter} to append notifications and by the worker and the
 * reconciler that process them.
 */
public interface WebhookInboxJpaRepository extends JpaRepository<WebhookInboxJpaEntity, Long> {

    /**
     * Lists the inbox rows ready for processing, oldest id first.
     *
     * <p>A row is eligible when it is {@code RECEIVED}, or {@code RETRY_PENDING} with a next
     * attempt that is due at {@code now}; the page limits the batch size.
     */
    @Query(
        "SELECT w FROM WebhookInboxJpaEntity w WHERE w.status = com.menta.billing.infrastructure.webhook."
            + "WebhookInboxStatus.RECEIVED OR (w.status = com.menta.billing.infrastructure.webhook."
            + "WebhookInboxStatus.RETRY_PENDING AND w.nextAttemptAt <= :now) ORDER BY w.id ASC"
    )
    List<WebhookInboxJpaEntity> findEligibleForProcessing(@Param("now") Instant now, Pageable pageable);
}
