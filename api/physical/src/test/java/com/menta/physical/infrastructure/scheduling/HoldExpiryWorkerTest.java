package com.menta.physical.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link HoldExpiryWorker} (#208, design B5, tasks Phase 9): the two GC deletes
 * run with the right cutoffs and batch size, and their counts are summed. The real SQL shape
 * (LIMIT, column filters) is proven separately against MySQL — see
 * {@code com.menta.app.integration.physical.HoldExpiryWorkerIntegrationTest}.
 */
class HoldExpiryWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    @Test
    void sweep_deletes_expired_unconverted_and_stale_converted_rows_with_the_configured_batch_size() {
        PhysicalCapacityHoldJpaRepository holdRepository = mock(PhysicalCapacityHoldJpaRepository.class);
        Clock clock = () -> NOW;
        when(holdRepository.deleteExpiredUnconverted(eq(NOW), eq(50))).thenReturn(3);
        when(holdRepository.deleteConvertedBefore(eq(NOW.minus(TTL)), eq(50))).thenReturn(2);

        HoldExpiryWorker worker = new HoldExpiryWorker(holdRepository, clock, TTL, 50);
        int total = worker.sweep();

        assertThat(total).isEqualTo(5);
        verify(holdRepository).deleteExpiredUnconverted(NOW, 50);
        verify(holdRepository).deleteConvertedBefore(NOW.minus(TTL), 50);
    }

    @Test
    void sweep_returns_zero_and_does_not_blow_up_when_nothing_is_stale() {
        PhysicalCapacityHoldJpaRepository holdRepository = mock(PhysicalCapacityHoldJpaRepository.class);
        Clock clock = () -> NOW;
        when(holdRepository.deleteExpiredUnconverted(any(Instant.class), eq(100))).thenReturn(0);
        when(holdRepository.deleteConvertedBefore(any(Instant.class), eq(100))).thenReturn(0);

        HoldExpiryWorker worker = new HoldExpiryWorker(holdRepository, clock, TTL, 100);

        assertThat(worker.sweep()).isZero();
    }
}
