package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import java.time.Instant;
import java.util.List;

/**
 * D7: shared, best-effort/non-binding coverage-availability check used by
 * every physical-purchase creation path ahead of the real reserving hold
 * (design C4). Extracted verbatim from {@link
 * CreatePhysicalPurchaseCheckoutUseCaseImpl}'s former {@code
 * resolveCoveragePlan} private method, which now delegates here — the
 * Mercado Pago flow still follows a Complete plan with
 * a real {@code PhysicalCapacityHoldPort#hold} (guarantee); the bank-transfer
 * flow (#36, US-BILLING-008) follows it with nothing (courtesy). {@link
 * CoveragePlanner} stays port-free; this class is the only caller of both.
 */
final class PhysicalCoverageAvailability {

    private PhysicalCoverageAvailability() {
    }

    /**
     * A6/D5: resolves the same eligible-session set the confirmation-time
     * outbox handler resolves, but with {@code clock.now()} instead of
     * {@code confirmedAt} and {@code requireAvailable = true} — a session
     * with zero visible spots is treated as not found here, ahead of the
     * real reserving hold call.
     */
    static CoveragePlanner.Plan.Complete requireComplete(
        PhysicalCourseAvailabilityPort availabilityPort, PhysicalCourseQuote quote, Instant now
    ) {
        // INDIVIDUAL's selectedSessionId was picked at quote time and may sit
        // before `now` for a same-day class — same periodStart reasoning the
        // outbox handler already applies for the identical purchaseType split.
        Instant periodStart = quote.getPurchaseType() == PurchaseType.INDIVIDUAL ? Instant.EPOCH : now;
        List<ScheduledSessionSnapshot> scheduledSessions = availabilityPort.findScheduledSessions(
            quote.getCourseId(), periodStart, now.plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );

        CoveragePlanner.Plan plan = switch (quote.getPurchaseType()) {
            case MONTHLY -> CoveragePlanner.planMonthly(scheduledSessions, now, quote.getScheduledSessionCount(), true);
            case INDIVIDUAL -> CoveragePlanner.planIndividual(scheduledSessions, quote.getSelectedSessionId(), true);
        };

        if (!(plan instanceof CoveragePlanner.Plan.Complete complete)) {
            throw new PhysicalCapacityUnavailableException();
        }
        return complete;
    }
}
