package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure function that resolves the eligible session set a physical purchase
 * covers (design A4, A5, A6). No Spring, no JPA, no I/O — fed entirely by
 * the snapshot list Billing's own out port {@code
 * com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort}
 * already returns. This class never calls that port itself: the caller
 * resolves what window to fetch and passes the result in, which is what
 * keeps this trivially unit-testable and carries this module's 100%
 * domain+application coverage floor on its own.
 *
 * <p>The same two entry points are meant to serve both callers this design
 * names (A6): the checkout endpoint plans with {@code clock.now()} and
 * {@code requireAvailable = true} (a session with zero available spots does
 * not count, feeding the best-effort {@code 409}); the outbox handler plans
 * with the payment's {@code confirmedAt} and {@code requireAvailable =
 * false} (a session that merely reads full today may still resolve at real
 * assignment time — confirmation reserves by attempting the real insert, not
 * by asking this function to pre-filter on availability). Neither the
 * calling site nor the instant choice is this class's concern.</p>
 */
public final class CoveragePlanner {

    /**
     * D4/A4: a shortfall past this many days from the reference instant is a
     * genuine impossibility, not a scheduling delay — the search terminates
     * here instead of scanning an abandoned course's calendar indefinitely.
     */
    public static final Duration COVERAGE_LOOKAHEAD = Duration.ofDays(120);

    private static final Comparator<ScheduledSessionSnapshot> CLAIM_ORDER = Comparator
        .comparing(ScheduledSessionSnapshot::scheduledAt)
        .thenComparing(ScheduledSessionSnapshot::sessionId);

    private CoveragePlanner() {
        // Static pure functions only.
    }

    /**
     * MONTHLY coverage (design A4): the next {@code scheduledSessionCount}
     * sessions in {@code scheduledSessions} with {@code scheduledAt >=
     * referenceInstant}, extended forward — never truncated — up to {@link
     * #COVERAGE_LOOKAHEAD} past {@code referenceInstant}. A gap in the
     * calendar is simply skipped; sessions beyond it still count toward the
     * total.
     *
     * <p>{@code scheduledSessions} must already cover at least {@code
     * [referenceInstant, referenceInstant + COVERAGE_LOOKAHEAD)} — this
     * function only filters and orders what it is given, it never queries
     * for more itself. A caller that fetches a narrower window risks a false
     * {@link Plan.Insufficient} for sessions that exist just outside it.</p>
     *
     * @param requireAvailable when {@code true}, a session with {@code
     *     availableSpots <= 0} is treated as if it did not exist (A6's
     *     best-effort {@code 409}); when {@code false}, availability is
     *     ignored here and the real assignment attempt is the actual gate
     *     (A4).
     * @return {@link Plan.Complete} carrying exactly {@code
     *     scheduledSessionCount} sessions ordered {@code (scheduledAt ASC,
     *     sessionId ASC)}, or {@link Plan.Insufficient} when fewer than that
     *     many exist inside the horizon.
     */
    public static Plan planMonthly(
        List<ScheduledSessionSnapshot> scheduledSessions, Instant referenceInstant, int scheduledSessionCount,
        boolean requireAvailable
    ) {
        Objects.requireNonNull(scheduledSessions, "scheduledSessions cannot be null");
        Objects.requireNonNull(referenceInstant, "referenceInstant cannot be null");
        if (scheduledSessionCount <= 0) {
            throw new IllegalArgumentException("scheduledSessionCount must be greater than zero");
        }

        Instant horizonEnd = referenceInstant.plus(COVERAGE_LOOKAHEAD);

        List<EligibleSession> eligible = scheduledSessions.stream()
            .filter(session -> !session.scheduledAt().isBefore(referenceInstant))
            .filter(session -> session.scheduledAt().isBefore(horizonEnd))
            .filter(session -> !requireAvailable || session.availableSpots() > 0)
            .sorted(CLAIM_ORDER)
            .limit(scheduledSessionCount)
            .map(session -> new EligibleSession(session.sessionId(), session.scheduledAt()))
            .toList();

        return eligible.size() < scheduledSessionCount ? new Plan.Insufficient() : new Plan.Complete(eligible);
    }

    /**
     * INDIVIDUAL coverage: exactly the quote's own {@code selectedSessionId}
     * — design A4 states it "does not go through the planner's window at
     * all". Still resolved against {@code scheduledSessions} so its {@code
     * scheduledAt} is known to the caller (needed to build a {@code
     * MultiSessionCapacityAssignmentCommand.SessionClaim}), and so a session
     * removed from the calendar — or emptied — between quote and
     * confirmation surfaces as {@link Plan.Insufficient} rather than a
     * stale, fabricated result.
     *
     * @param requireAvailable same meaning as in {@link #planMonthly}: when
     *     {@code true}, a selected session with {@code availableSpots <= 0}
     *     counts as not found (A6's best-effort {@code 409}).
     */
    public static Plan planIndividual(
        List<ScheduledSessionSnapshot> scheduledSessions, String selectedSessionId, boolean requireAvailable
    ) {
        Objects.requireNonNull(scheduledSessions, "scheduledSessions cannot be null");
        Objects.requireNonNull(selectedSessionId, "selectedSessionId cannot be null");

        return scheduledSessions.stream()
            .filter(session -> session.sessionId().equals(selectedSessionId))
            .filter(session -> !requireAvailable || session.availableSpots() > 0)
            .findFirst()
            .<Plan>map(session -> new Plan.Complete(List.of(new EligibleSession(session.sessionId(), session.scheduledAt()))))
            .orElseGet(Plan.Insufficient::new);
    }

    /**
     * One session a purchase covers, in the exact order it must be claimed
     * (design A2) — carries {@code scheduledAt} because that order is what
     * later feeds a {@code MultiSessionCapacityAssignmentCommand.SessionClaim}
     * unchanged, with no separate sort that could drift out of sync.
     */
    public record EligibleSession(String sessionId, Instant scheduledAt) {

        public EligibleSession {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(scheduledAt, "scheduledAt cannot be null");
        }
    }

    /**
     * Outcome of a coverage computation (design A4) — sealed so the compiler
     * enforces exhaustive handling by every caller, mirroring this module's
     * {@link com.menta.billing.application.dto.CurrentSubscriptionResult}.
     */
    public sealed interface Plan {

        /**
         * The purchase's full eligible-session set, already ordered {@code
         * (scheduledAt ASC, sessionId ASC)} — no further sort is needed or
         * expected before building a claim command from it.
         */
        record Complete(List<EligibleSession> sessions) implements Plan {

            public Complete {
                Objects.requireNonNull(sessions, "sessions cannot be null");
                sessions = List.copyOf(sessions);
            }
        }

        /**
         * Fewer eligible sessions exist than the purchase requires, even
         * after extending the search to {@link #COVERAGE_LOOKAHEAD} (design
         * A4) — a genuine impossibility, never a partial list. The caller
         * decides the consequence: the outbox handler routes to {@code
         * EXCEPTION / TARGET_NOT_SCHEDULED}; the checkout endpoint rejects
         * with {@code 409 CAPACITY_UNAVAILABLE} (A6).
         */
        record Insufficient() implements Plan {
        }
    }
}
