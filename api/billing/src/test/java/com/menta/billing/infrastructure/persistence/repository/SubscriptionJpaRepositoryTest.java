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
            NOW.minusSeconds(DAY), userId, null, "PAID", null, null, null, null, 0L
        );
    }

    private static SubscriptionJpaEntity pendingRowWithNoEndDate(UUID userId, UUID planId) {
        UUID id = UUID.randomUUID();
        return new SubscriptionJpaEntity(
            id, UUID.randomUUID(), userId, planId, "idem-" + id, null,
            SubscriptionStatus.PENDING.name(), FulfillmentStatus.PENDING_FULFILLMENT.name(),
            null, null, null, null, NOW.minusSeconds(DAY), null, null, null, "PAID", null, null, null, null, 0L
        );
    }

    /**
     * US-BILLING-004 (design.md A1): {@code findCurrentByUserId} resolves through {@code
     * active_user_id}, which is released the moment a row leaves PENDING/ACTIVE, so it structurally
     * cannot return an EXPIRED row. This derived query is the only way {@code GET /me} can resolve
     * the third display state.
     */
    @Test
    void finds_the_latest_expired_row_by_end_date_when_several_exist() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        SubscriptionJpaEntity older = expiredRow(userId, planId, NOW.minusSeconds(60 * DAY));
        SubscriptionJpaEntity latest = expiredRow(userId, planId, NOW.minusSeconds(DAY));
        SubscriptionJpaEntity otherUser = expiredRow(UUID.randomUUID(), planId, NOW.minusSeconds(1));
        repository.saveAllAndFlush(List.of(older, latest, otherUser));

        Optional<SubscriptionJpaEntity> found = repository
            .findFirstByUserIdAndStatusOrderByEndDateDesc(userId, SubscriptionStatus.EXPIRED.name());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    void finds_no_expired_row_when_the_user_has_none() {
        UUID userId = UUID.randomUUID();

        Optional<SubscriptionJpaEntity> found = repository
            .findFirstByUserIdAndStatusOrderByEndDateDesc(userId, SubscriptionStatus.EXPIRED.name());

        assertThat(found).isEmpty();
    }

    /** History (design.md A3): every row for the user, newest created first, regardless of status. */
    @Test
    void finds_all_rows_for_the_user_ordered_newest_created_first() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        SubscriptionJpaEntity oldest = rowCreatedAt(userId, planId, SubscriptionStatus.EXPIRED.name(), NOW.minusSeconds(3 * DAY));
        SubscriptionJpaEntity middle = rowCreatedAt(userId, planId, SubscriptionStatus.CANCELLED.name(), NOW.minusSeconds(2 * DAY));
        SubscriptionJpaEntity newest = rowCreatedAt(userId, planId, SubscriptionStatus.ACTIVE.name(), NOW.minusSeconds(DAY));
        SubscriptionJpaEntity otherUser = rowCreatedAt(UUID.randomUUID(), planId, SubscriptionStatus.ACTIVE.name(), NOW);
        repository.saveAllAndFlush(List.of(oldest, middle, newest, otherUser));

        List<SubscriptionJpaEntity> found = repository.findAllByUserIdOrderByCreatedAtDesc(userId);

        assertThat(found).extracting(SubscriptionJpaEntity::getId)
            .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void finds_no_rows_when_the_user_has_none() {
        assertThat(repository.findAllByUserIdOrderByCreatedAtDesc(UUID.randomUUID())).isEmpty();
    }

    private static SubscriptionJpaEntity expiredRow(UUID userId, UUID planId, Instant endDate) {
        UUID id = UUID.randomUUID();
        return new SubscriptionJpaEntity(
            id, UUID.randomUUID(), userId, planId, "idem-" + id, null,
            SubscriptionStatus.EXPIRED.name(), FulfillmentStatus.ASSIGNED.name(),
            NOW.minusSeconds(60 * DAY), endDate, null, null, NOW.minusSeconds(90 * DAY),
            null, null, null, "PAID", null, null, null, null, 0L
        );
    }

    private static SubscriptionJpaEntity rowCreatedAt(UUID userId, UUID planId, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        return new SubscriptionJpaEntity(
            id, UUID.randomUUID(), userId, planId, "idem-" + id, null,
            status, FulfillmentStatus.ASSIGNED.name(),
            createdAt, createdAt.plusSeconds(30 * DAY), null, null, createdAt,
            null, null, null, "PAID", null, null, null, null, 0L
        );
    }
}
