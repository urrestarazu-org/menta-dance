package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseSessionJpaRepository extends JpaRepository<PurchaseSessionJpaEntity, Long> {

    /**
     * A LOCKING read (#245), unconditionally — both of {@code
     * PurchaseRepositoryAdapter}'s two call sites need it, and neither has a
     * self-block risk from it:
     *
     * <ul>
     *   <li>{@code insert()}'s own "already inserted?" guard — the
     *       {@code purchaseId} being checked is either a brand-new random id
     *       nothing else has ever seen (first save/saveIsolated for this
     *       purchase — the lock and the following insert are the SAME
     *       transaction, so no cross-transaction wait), or an existing
     *       purchase's id being re-saved on a status transition, where
     *       locking the already-persisted rows is exactly the guarantee
     *       wanted.</li>
     *   <li>{@code toDomain()}'s session-list lookup for {@code
     *       findByPaymentIdForUpdate} — under REPEATABLE READ, a plain
     *       consistent read here would silently return an empty list for
     *       rows {@code saveIsolated} committed moments earlier on a
     *       different connection, and {@link
     *       com.menta.billing.domain.model.Purchase}'s constructor rejects an
     *       empty session list for any non-{@code EXCEPTION} status —
     *       exactly the failure this locking read prevents.</li>
     * </ul>
     */
    @Query(
        value = "SELECT * FROM billing_purchase_sessions WHERE purchase_id = :purchaseId "
            + "ORDER BY position ASC FOR UPDATE",
        nativeQuery = true
    )
    List<PurchaseSessionJpaEntity> findByPurchaseIdOrderByPositionAsc(@Param("purchaseId") UUID purchaseId);

    /**
     * A derived, NON-locking batch read for the admin EXCEPTION-purchases list (#237, design C3):
     * one query per page, grouped by {@code purchaseId} in memory afterward. Do NOT confuse this
     * with {@link #findByPurchaseIdOrderByPositionAsc(UUID)} above — that one is a NATIVE {@code
     * FOR UPDATE} locking query for the fulfillment writers. A read-only admin list has no
     * business taking row locks, and it must not be issued once per purchase (N+1) across a page.
     * Served by {@code uq_billing_purchase_sessions_purchase_position (purchase_id, position)}
     * (V20) — no new index.
     */
    List<PurchaseSessionJpaEntity> findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(
        Collection<UUID> purchaseIds);
}
