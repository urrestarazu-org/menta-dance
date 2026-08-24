package com.menta.billing.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link BillingOutboxRowJpaEntity}.
 *
 * <p>The reconciler (api:app) cross-module reads PENDING rows through the
 * auth-side repository (the table is shared, see
 * {@link BillingOutboxRowJpaEntity}'s Javadoc); writes from :api:billing
 * go through this repository.</p>
 */
@Repository
public interface BillingOutboxRowJpaRepository extends JpaRepository<BillingOutboxRowJpaEntity, Long> {
}
