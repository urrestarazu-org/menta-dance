package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.domain.exception.NoNewSessionsToPurchaseException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
     * real reserving hold call. For MONTHLY, sessions {@code studentId} already
     * owns are skipped (see {@link CoveragePlanner#planMonthly}); and when the
     * student owns <em>every</em> session the monthly would cover, the purchase
     * is refused with {@link NoNewSessionsToPurchaseException} (#541) before
     * any hold or {@code Payment} exists.
     */
    static CoveragePlanner.Plan.Complete requireComplete(
        PhysicalCourseAvailabilityPort availabilityPort, PhysicalCourseQuote quote, UUID studentId, Instant now
    ) {
        // INDIVIDUAL's selectedSessionId was picked at quote time and may sit
        // before `now` for a same-day class — same periodStart reasoning the
        // outbox handler already applies for the identical purchaseType split.
        Instant periodStart = quote.getPurchaseType() == PurchaseType.INDIVIDUAL ? Instant.EPOCH : now;
        List<ScheduledSessionSnapshot> scheduledSessions = availabilityPort.findScheduledSessions(
            quote.getCourseId(), periodStart, now.plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );

        CoveragePlanner.Plan plan = switch (quote.getPurchaseType()) {
            case MONTHLY -> {
                Set<String> owned = ownedSessionIds(availabilityPort, quote.getCourseId(), studentId, now);
                rejectWhenNothingNew(scheduledSessions, now, quote.getScheduledSessionCount(), owned);
                yield CoveragePlanner.planMonthly(
                    scheduledSessions, now, quote.getScheduledSessionCount(), true, owned
                );
            }
            case INDIVIDUAL -> CoveragePlanner.planIndividual(scheduledSessions, quote.getSelectedSessionId(), true);
        };

        if (!(plan instanceof CoveragePlanner.Plan.Complete complete)) {
            throw new PhysicalCapacityUnavailableException();
        }
        return complete;
    }

    /**
     * #541: refuses a MONTHLY quote when the student already owns every
     * session the monthly would cover from {@code now}. Same rule, same
     * window and same planner function the checkout applies, so a quote that
     * passes here is never rejected at checkout for this reason alone (apart
     * from changes in between). The calendar is only fetched when the student
     * owns something at all.
     */
    static void requireNewSessions(
        PhysicalCourseAvailabilityPort availabilityPort, String courseId, UUID studentId, int scheduledSessionCount,
        Instant now
    ) {
        Set<String> owned = ownedSessionIds(availabilityPort, courseId, studentId, now);
        if (owned.isEmpty()) {
            return;
        }
        List<ScheduledSessionSnapshot> scheduledSessions = availabilityPort.findScheduledSessions(
            courseId, now, now.plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );
        rejectWhenNothingNew(scheduledSessions, now, scheduledSessionCount, owned);
    }

    private static void rejectWhenNothingNew(
        List<ScheduledSessionSnapshot> scheduledSessions, Instant now, int scheduledSessionCount, Set<String> owned
    ) {
        if (CoveragePlanner.monthlyAddsNoNewSession(scheduledSessions, now, scheduledSessionCount, owned)) {
            throw new NoNewSessionsToPurchaseException();
        }
    }

    /**
     * Sessions the student already holds a confirmed assignment for inside the
     * planner's own window: a MONTHLY purchase skips them, because assigning
     * the same student to the same session twice is rejected by the database.
     */
    private static Set<String> ownedSessionIds(
        PhysicalCourseAvailabilityPort availabilityPort, String courseId, UUID studentId, Instant now
    ) {
        return availabilityPort.findSessionIdsAssignedTo(
            courseId, studentId, now, now.plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );
    }
}
