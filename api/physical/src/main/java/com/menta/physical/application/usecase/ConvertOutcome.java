package com.menta.physical.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of {@link ConvertCapacityHoldUseCase#convertAll} (design B3,
 * "Data Flow"):
 *
 * <ul>
 *   <li>{@link HoldNotFound} — zero rows for {@code paymentId}: the payment
 *       never had a hold (legacy/rollback path). The caller falls back to
 *       {@code CoveragePlanner} + {@code assignAll}.</li>
 *   <li>{@link AlreadyConverted} — every row for {@code paymentId} already
 *       has {@code converted_at IS NOT NULL}: a redelivered webhook,
 *       no-op.</li>
 *   <li>{@link Converted} — the first successful conversion; carries the
 *       held session ids in claim order.</li>
 * </ul>
 *
 * <p>Sealed so the producer cannot accidentally widen the result set,
 * same discipline as {@link AssignmentOutcome}.</p>
 */
public sealed interface ConvertOutcome permits ConvertOutcome.HoldNotFound,
    ConvertOutcome.AlreadyConverted, ConvertOutcome.Converted {

    final class HoldNotFound implements ConvertOutcome {
        public static final HoldNotFound INSTANCE = new HoldNotFound();

        private HoldNotFound() {
        }
    }

    final class AlreadyConverted implements ConvertOutcome {
        public static final AlreadyConverted INSTANCE = new AlreadyConverted();

        private AlreadyConverted() {
        }
    }

    record Converted(List<UUID> sessionIds) implements ConvertOutcome {

        public Converted {
            Objects.requireNonNull(sessionIds, "sessionIds cannot be null");
            sessionIds = List.copyOf(sessionIds);
        }
    }
}
