package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Verifies that a missing presential handler remains a visible failure. */
class PhysicalCapacityAssignmentOutboxEventHandlerNoHandlerTest {

    @Test
    void missing_presential_handler_marks_the_row_failed_with_a_literal_diagnostic() {
        OutboxRowJpaRepository repository = Mockito.mock(OutboxRowJpaRepository.class);
        OutboxReconciliationWorker worker = new OutboxReconciliationWorker(repository, List.of(), 30L);
        OutboxRowJpaEntity row = new OutboxRowJpaEntity(
            "01HFPRESENTIAL0000000000000000",
            "billing.PhysicalPaymentCompleted",
            "55555555-5555-5555-5555-555555555555",
            "{\"payload\":\"placeholder\"}",
            OutboxStatus.PENDING,
            0,
            null,
            Instant.now().plusSeconds(60),
            Instant.now(),
            null
        );

        boolean failed = worker.process(row);

        assertThat(failed).isTrue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo(
            "No handler registered for event type: billing.PhysicalPaymentCompleted"
        );
    }
}
