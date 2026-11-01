package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxStatusMetricsTest {

    @Mock private OutboxRowJpaRepository repository;

    @Test
    void exposes_failed_and_dead_row_counts_as_gauges_read_on_every_scrape() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(3L, 5L);
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(2L);

        new OutboxStatusMetrics(repository, registry);

        var failed = registry.get(OutboxStatusMetrics.GAUGE_NAME)
            .tag(OutboxStatusMetrics.STATUS_TAG, "FAILED").gauge();
        var dead = registry.get(OutboxStatusMetrics.GAUGE_NAME)
            .tag(OutboxStatusMetrics.STATUS_TAG, "DEAD").gauge();
        assertThat(failed.value()).isEqualTo(3.0);
        assertThat(failed.value()).isEqualTo(5.0);
        assertThat(dead.value()).isEqualTo(2.0);
    }

    @Test
    void does_not_register_gauges_for_healthy_statuses() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OutboxStatusMetrics(repository, registry);

        assertThat(registry.find(OutboxStatusMetrics.GAUGE_NAME).gauges()).hasSize(2);
    }
}
