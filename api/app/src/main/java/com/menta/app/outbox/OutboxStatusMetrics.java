package com.menta.app.outbox;

import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes how many outbox rows sit in a state that needs an operator (#672).
 *
 * <p>One gauge, {@value #GAUGE_NAME}, tagged {@value #STATUS_TAG} with {@code FAILED} (retrying
 * with backoff; a short-lived non-zero value is normal during a Redis blip) and {@code DEAD}
 * (terminal; never retried again, any non-zero value needs a look). It is read from the database on
 * every scrape, so it stays correct across restarts and after a manual requeue. The log-based
 * Grafana rule {@code auth-outbox-dead.yml} covers the moment a row dies.</p>
 */
@Component
public class OutboxStatusMetrics {

    /** Gauge name; the only tag is {@link #STATUS_TAG}. */
    public static final String GAUGE_NAME = "auth.outbox.rows";

    public static final String STATUS_TAG = "status";

    /** Registers one gauge per status that signals trouble. */
    public OutboxStatusMetrics(OutboxRowJpaRepository repository, MeterRegistry meterRegistry) {
        for (OutboxStatus status : new OutboxStatus[] {OutboxStatus.FAILED, OutboxStatus.DEAD}) {
            Gauge.builder(GAUGE_NAME, repository, repo -> repo.countByStatus(status))
                .tag(STATUS_TAG, status.name())
                .description("Outbox rows currently in the given status")
                .register(meterRegistry);
        }
    }
}
