package com.menta.billing.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * D3 (US-BILLING-011): proves the derived query behind {@code
 * findLatestCancelledWithRemainingAccess} discriminates real rows through a real Spring Data
 * query execution, not a mock.
 *
 * <p>{@code SubscriptionRepositoryAdapterTest} only proves the adapter wires column values
 * through to a stubbed repository call — it never runs {@code
 * findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc} against actual rows. The
 * {@code api/app} integration test only exercises the checkout flow, which seeds at most one
 * cancelled row per test and never a distractor that the query must reject. A subtle bug in this
 * derived-query method name (wrong keyword, transposed parameter order) would compile and pass
 * both of those, while silently either suppressing a legitimate overlap notice or leaking one for
 * the wrong plan/expired period/never-activated subscription.</p>
 */
@DataJpaTest
@ContextConfiguration(classes = SubscriptionJpaRepositoryTest.JpaConfiguration.class)
class SubscriptionJpaRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final long DAY = 86_400L;

    @Autowired private SubscriptionJpaRepository repository;

    @Configuration
    @EntityScan(basePackageClasses = SubscriptionJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = SubscriptionJpaRepository.class)
    static class JpaConfiguration { }

    /**
     * Seeds 5 rows for the same user: the one row that must match, three distractors that must
     * each be excluded for a different reason, and a second matching row with a closer {@code
     * endDate} that must lose to the farther one — proving {@code ORDER BY endDate DESC} picks
     * the row with the most remaining access (design.md D3/A8), not simply the most recent
     * cancellation.
     */
    @Test
    void finds_only_the_latest_cancelled_row_for_the_same_user_and_plan_with_remaining_access() {
        UUID userId = UUID.randomUUID();
        UUID planX = UUID.randomUUID();
        UUID planY = UUID.randomUUID();

        SubscriptionJpaEntity matching = cancelledRow(userId, planX, NOW.plusSeconds(30 * DAY));
        SubscriptionJpaEntity wrongPlan = cancelledRow(userId, planY, NOW.plusSeconds(30 * DAY));
        SubscriptionJpaEntity expired = cancelledRow(userId, planX, NOW.minusSeconds(DAY));
        SubscriptionJpaEntity neverActivated = pendingRowWithNoEndDate(userId, planX);
        SubscriptionJpaEntity closerButStillMatching = cancelledRow(userId, planX, NOW.plusSeconds(5 * DAY));
        repository.saveAllAndFlush(
            List.of(matching, wrongPlan, expired, neverActivated, closerButStillMatching)
        );

        Optional<SubscriptionJpaEntity> found = repository
            .findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc(
                userId, planX, SubscriptionStatus.CANCELLED.name(), NOW
            );

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(matching.getId());
    }

    private static SubscriptionJpaEntity cancelledRow(UUID userId, UUID planId, Instant endDate) {
        UUID id = UUID.randomUUID();
        return new SubscriptionJpaEntity(
            id, UUID.randomUUID(), userId, planId, "idem-" + id, null,
            SubscriptionStatus.CANCELLED.name(), FulfillmentStatus.ASSIGNED.name(),
            NOW.minusSeconds(60 * DAY), endDate, null, null, NOW.minusSeconds(90 * DAY),
            NOW.minusSeconds(DAY), userId, null
        );
    }

    private static SubscriptionJpaEntity pendingRowWithNoEndDate(UUID userId, UUID planId) {
        UUID id = UUID.randomUUID();
        return new SubscriptionJpaEntity(
            id, UUID.randomUUID(), userId, planId, "idem-" + id, null,
            SubscriptionStatus.PENDING.name(), FulfillmentStatus.PENDING_FULFILLMENT.name(),
            null, null, null, null, NOW.minusSeconds(DAY), null, null, null
        );
    }
}
