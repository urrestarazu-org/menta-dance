package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.shared.outbox.OutboxStatus;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for common_outbox_events.
 *
 * The reconciler (api:app) reads PENDING rows via findByStatusOrderByIdAsc.
 * Writes from :api:auth go through JpaRepository#save.
 *
 * Pagination is the throttle: a single tick fetches at most `pageSize` rows
 * so a stuck producer cannot monopolize the reconciler thread.
 */
@Repository
public interface OutboxRowJpaRepository extends JpaRepository<OutboxRowJpaEntity, Long> {

    List<OutboxRowJpaEntity> findByStatusOrderByIdAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    /**
     * Delete processed outbox events older than the given cutoff.
     * Used by the retention cleanup job to prevent unbounded table growth.
     *
     * @param status   typically COMPLETED (successfully processed events)
     * @param cutoff   events with processed_at before this instant are deleted
     * @return number of deleted rows
     */
    long deleteByStatusAndProcessedAtBefore(OutboxStatus status, Instant cutoff);
}
