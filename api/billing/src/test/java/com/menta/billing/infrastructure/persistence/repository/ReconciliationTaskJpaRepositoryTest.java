package com.menta.billing.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.infrastructure.persistence.entity.ReconciliationTaskJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * #33, US-BILLING-005, design D2/C11 — {@code resolveOpenByPaymentId} is a conditional bulk
 * update ({@code AND resolved = false}), so a derived-query-style unit needs a real query
 * execution, not a mock.
 */
@DataJpaTest
@ContextConfiguration(classes = ReconciliationTaskJpaRepositoryTest.JpaConfiguration.class)
class ReconciliationTaskJpaRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Autowired private ReconciliationTaskJpaRepository repository;

    @Configuration
    @EntityScan(basePackageClasses = ReconciliationTaskJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = ReconciliationTaskJpaRepository.class)
    static class JpaConfiguration {
    }

    private ReconciliationTaskJpaEntity openTaskFor(UUID paymentId) {
        return new ReconciliationTaskJpaEntity(
            UUID.randomUUID(), paymentId, "mp-1", "mismatch", NOW.minusSeconds(60)
        );
    }

    @Test
    void resolves_an_open_task_tied_to_the_payment() {
        UUID paymentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ReconciliationTaskJpaEntity task = repository.saveAndFlush(openTaskFor(paymentId));

        int updated = repository.resolveOpenByPaymentId(paymentId, NOW, adminId);
        repository.flush();

        assertThat(updated).isEqualTo(1);
        ReconciliationTaskJpaEntity reloaded = repository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.isResolved()).isTrue();
        assertThat(reloaded.getResolvedAt()).isEqualTo(NOW);
        assertThat(reloaded.getResolvedBy()).isEqualTo(adminId);
    }

    @Test
    void re_running_on_an_already_resolved_row_is_a_no_op() {
        UUID paymentId = UUID.randomUUID();
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        ReconciliationTaskJpaEntity task = repository.saveAndFlush(openTaskFor(paymentId));
        repository.resolveOpenByPaymentId(paymentId, NOW, firstAdmin);
        repository.flush();

        int updatedAgain = repository.resolveOpenByPaymentId(paymentId, NOW.plusSeconds(60), secondAdmin);
        repository.flush();

        assertThat(updatedAgain).isEqualTo(0);
        ReconciliationTaskJpaEntity reloaded = repository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getResolvedBy()).isEqualTo(firstAdmin);
    }

    @Test
    void a_payment_with_no_tied_task_returns_zero_with_no_exception() {
        int updated = repository.resolveOpenByPaymentId(UUID.randomUUID(), NOW, UUID.randomUUID());

        assertThat(updated).isZero();
    }
}
