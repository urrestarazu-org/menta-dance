package com.menta.app.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.app.billing.MarkPurchaseAssignedAdapter;
import com.menta.app.billing.MarkPurchaseExceptionAdapter;
import com.menta.app.billing.PhysicalCapacityAssignmentAdapter;
import com.menta.app.billing.PublishPaymentFulfillmentFailedAdapter;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.in.PurchaseCreationFromEventPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.application.usecase.CoveragePlanner;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.Reason;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import com.menta.shared.physical.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Spring-discovered handler (proposal §4 handler; design §6 reconciler
 * integration). Resolves {@code billing.PhysicalPaymentCompleted} events:
 * upserts a {@code Purchase(PENDING_FULFILLMENT)}, assigns capacity via
 * Physical's IN port, and routes any {@link
 * com.menta.physical.domain.exception.CapacityBelowAssignedException} to
 * {@link MarkPurchaseExceptionAdapter} (the EXCEPTION residual).
 *
 * <p>Unexpected exceptions propagate so the worker keeps the
 * {@code FAILED/backoff} lifecycle per design §9 R9.</p>
 *
 * <h2>studentId resolution</h2>
 * <p>{@code MultiSessionCapacityAssignmentCommand} requires a {@code
 * studentId}; the payload only carries {@code paymentId, targetReference}.
 * We load the underlying {@link Payment} via {@link PaymentRepository} and
 * reuse its {@link Payment#userId} as the student id. If the payment row is
 * missing (e.g. deletion by an administrator before reconciliation), we
 * route through {@link MarkPurchaseExceptionAdapter} with {@link
 * Reason#TARGET_NOT_SCHEDULED} — a conservative terminal classification
 * rather than a silent dead-letter.</p>
 *
 * <h2>Purchase row built directly at EXCEPTION (#238)</h2>
 * <p>Each of the three sites where no {@code Purchase} row can ever exist yet
 * (this branch, the quote-not-found branch, and the coverage-shortfall
 * branch) calls {@link PurchaseCreationFromEventPort#createPurchaseFromPaymentEvent}
 * with an EMPTY session list and never calls {@code markException} afterward.
 * {@code createPurchaseFromPaymentEvent} only needs {@code payload} — never
 * {@code payment} or a resolved {@code userId} — so this runs unconditionally,
 * even when {@code payment} itself is {@code null}.</p>
 *
 * <p>An earlier attempt at this fix called {@code createPurchaseFromPaymentEvent}
 * and then {@code markException} — mirroring the {@code CapacityBelowAssignedException}
 * catch blocks below. That does NOT work here: {@code createPurchaseFromPaymentEvent}
 * builds via {@code Purchase.pendingFulfillment}, whose constructor rejects an
 * empty session list for every status except {@code EXCEPTION}, so the same
 * {@code IllegalArgumentException} fires before {@code markException} ever
 * runs. There is no legitimate intermediate {@code PENDING_FULFILLMENT} state
 * for a payment that never resolved to any schedulable session in the first
 * place, so {@code createPurchaseFromPaymentEvent} now builds these three
 * cases directly via {@link com.menta.billing.domain.model.Purchase#exception}
 * when {@code eligibleSessionIds} is empty — the row is already at {@code
 * FulfillmentStatus.EXCEPTION} once created, so {@code markException} would
 * only be a documented no-op (see its own {@code EXCEPTION -> EXCEPTION}
 * branch) and is skipped entirely at these three sites.</p>
 *
 * <h2>Quote resolution and coverage (#41 PR6)</h2>
 * <p>{@link com.menta.billing.domain.model.PaymentTarget.Physical}'s
 * reference is the {@code quoteId}, not a session id (design A5). This
 * handler loads the real {@link PhysicalCourseQuote} for that reference and
 * runs Billing's {@link CoveragePlanner} to resolve the concrete
 * eligible-session set: {@code MONTHLY} counts forward from {@code
 * confirmedAt} for {@code scheduledSessionCount} sessions (design A4);
 * {@code INDIVIDUAL} is exactly the quote's own {@code selectedSessionId}.
 * A quote reference that resolves to nothing, or a coverage shortfall that
 * persists past {@link CoveragePlanner#COVERAGE_LOOKAHEAD}, routes through
 * {@link MarkPurchaseExceptionAdapter} with {@link
 * Reason#TARGET_NOT_SCHEDULED} — no {@code Purchase} row and no partial
 * assignment are ever written for an unfillable set. Once the eligible set
 * is known, one ordered, all-or-nothing {@link
 * MultiSessionCapacityAssignmentCommand} is built and claimed via {@link
 * PhysicalCapacityAssignmentAdapter#assignAll} (design A3).</p>
 */
@Component
public class PhysicalCapacityAssignmentOutboxEventHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(
        PhysicalCapacityAssignmentOutboxEventHandler.class
    );

    private final PhysicalCapacityAssignmentAdapter capacityAdapter;
    private final MarkPurchaseExceptionAdapter exceptionAdapter;
    private final MarkPurchaseAssignedAdapter assignedAdapter;
    private final PurchaseCreationFromEventPort purchaseCreationFromEventPort;
    private final PaymentRepository paymentRepository;
    private final PhysicalCourseQuoteRepository quoteRepository;
    private final PhysicalCourseAvailabilityPort courseAvailabilityPort;
    private final com.menta.physical.application.port.in.PhysicalCapacityHoldPort physicalCapacityHoldPort;
    private final PublishPaymentFulfillmentFailedAdapter publishPaymentFulfillmentFailedAdapter;
    private final ObjectMapper objectMapper;

    public PhysicalCapacityAssignmentOutboxEventHandler(
        PhysicalCapacityAssignmentAdapter capacityAdapter,
        MarkPurchaseExceptionAdapter exceptionAdapter,
        MarkPurchaseAssignedAdapter assignedAdapter,
        PurchaseCreationFromEventPort purchaseCreationFromEventPort,
        PaymentRepository paymentRepository,
        PhysicalCourseQuoteRepository quoteRepository,
        PhysicalCourseAvailabilityPort courseAvailabilityPort,
        com.menta.physical.application.port.in.PhysicalCapacityHoldPort physicalCapacityHoldPort,
        PublishPaymentFulfillmentFailedAdapter publishPaymentFulfillmentFailedAdapter,
        ObjectMapper objectMapper
    ) {
        this.capacityAdapter = capacityAdapter;
        this.exceptionAdapter = exceptionAdapter;
        this.assignedAdapter = assignedAdapter;
        this.purchaseCreationFromEventPort = purchaseCreationFromEventPort;
        this.paymentRepository = paymentRepository;
        this.quoteRepository = quoteRepository;
        this.courseAvailabilityPort = courseAvailabilityPort;
        this.physicalCapacityHoldPort = physicalCapacityHoldPort;
        this.publishPaymentFulfillmentFailedAdapter = publishPaymentFulfillmentFailedAdapter;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        PaymentCompletedOutboxPayload payload = parse(row);
        com.menta.billing.domain.model.PaymentId paymentId = com.menta.billing.domain.model.PaymentId.of(
            payload.paymentId()
        );

        // Resolve the buyer from the payment row BEFORE upserting the
        // Purchase: a missing target or non-Physical target means the
        // hold already expired / coverage was rewritten — route directly
        // to EXCEPTION and skip the upsert so we don't write a
        // PENDING_FULFILLMENT row that has to be re-read on every retry.
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !(payment.getTarget() instanceof PaymentTarget.Physical physical)) {
            log.warn(
                "Payment row absent or non-Physical for outbox event paymentId={}; routing to EXCEPTION",
                payload.paymentId()
            );
            UUID userId = payment != null ? payment.getUserId() : null;
            publishPaymentFulfillmentFailed(paymentId, userId, Reason.TARGET_NOT_SCHEDULED);
            // #238: createPurchaseFromPaymentEvent only needs payload (already
            // parsed above), never payment/userId — so this runs even when
            // payment itself is null. An empty eligibleSessionIds list builds
            // the row directly at FulfillmentStatus.EXCEPTION (see the class
            // javadoc) — no markException call is needed or made here.
            purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, List.of());
            return;
        }

        // Design B3/step 8: try converting an existing hold first. A held
        // purchase never recomputes via CoveragePlanner (D7) — the hold's
        // own session set, fixed atomically at checkout time, is the source
        // of truth for what gets assigned. Only HoldNotFound (the
        // rollback/legacy path — no hold ever existed for this payment)
        // falls through to today's unchanged CoveragePlanner + assignAll.
        com.menta.physical.application.usecase.ConvertOutcome convertOutcome;
        try {
            convertOutcome = physicalCapacityHoldPort.convertAll(payload.paymentId(), payment.getUserId());
        } catch (com.menta.physical.domain.exception.CapacityBelowAssignedException capacityTripped) {
            // Spec scenario (8.7): a held session vanished/oversold before
            // conversion — all-or-nothing, zero assignment rows survive the
            // rolled-back REQUIRES_NEW transaction. Same EXCEPTION routing,
            // same idempotent-redelivery discipline, as the legacy path.
            //
            // Unlike the legacy path, createPurchaseFromPaymentEvent has not
            // run yet for a held purchase (it only runs after a successful
            // conversion) — markException needs a PENDING_FULFILLMENT row to
            // transition, so build it here from the hold's own (still
            // unconverted, since the transaction above rolled back) session
            // set. This also keeps the pattern that already exists in
            // MarkPurchaseExceptionUseCase honest: without a Purchase row,
            // markException throws PaymentNotFoundException, which is not
            // covered by that method's noRollbackFor and would otherwise
            // mark the ambient outbox-worker transaction rollback-only.
            List<String> heldSessionIds = physicalCapacityHoldPort.heldSessionIds(payload.paymentId()).stream()
                .map(UUID::toString)
                .toList();
            purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, heldSessionIds);
            try {
                exceptionAdapter.markException(paymentId, Reason.CAPACITY_BELOW_ASSIGNED);
            } catch (com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException alreadyAssigned) {
                log.info(
                    "Redelivered outbox event for an already-ASSIGNED purchase paymentId={}; no-op",
                    payload.paymentId()
                );
            }
            return;
        }

        if (convertOutcome instanceof com.menta.physical.application.usecase.ConvertOutcome.Converted converted) {
            // design D7: the hold's own session set is authoritative, never
            // recomputed — no CoveragePlanner, no assignAll for this path.
            List<String> heldSessionIds = converted.sessionIds().stream().map(UUID::toString).toList();
            purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, heldSessionIds);
            assignedAdapter.markAssigned(paymentId);
            return;
        }
        if (convertOutcome instanceof com.menta.physical.application.usecase.ConvertOutcome.AlreadyConverted) {
            // Idempotent redelivery: the hold was already converted by an
            // earlier delivery. No-op, same discipline as the ASSIGNED ->
            // EXCEPTION refusal below.
            log.info(
                "Redelivered outbox event for an already-converted hold paymentId={}; no-op",
                payload.paymentId()
            );
            return;
        }
        // ConvertOutcome.HoldNotFound falls through to the legacy path
        // below, byte-identical to before this change (rollback layer 1).

        String quoteId = physical.quoteId();
        PhysicalCourseQuote quote = quoteRepository.findById(quoteId).orElse(null);
        if (quote == null) {
            log.warn(
                "PhysicalCourseQuote {} not found for outbox event paymentId={}; routing to EXCEPTION",
                quoteId, payload.paymentId()
            );
            publishPaymentFulfillmentFailed(paymentId, payment.getUserId(), Reason.TARGET_NOT_SCHEDULED);
            // #238: see the class javadoc on the payment==null branch above —
            // same direct-to-EXCEPTION construction, no markException call.
            purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, List.of());
            return;
        }

        // INDIVIDUAL's selected session was picked once at quote time and may
        // legitimately sit before confirmedAt (e.g. a same-day class bought
        // minutes before it starts) — design A4 says INDIVIDUAL "does not go
        // through the planner's window at all", so periodStart only narrows
        // to confirmedAt for MONTHLY, whose coverage is explicitly defined as
        // counting forward FROM confirmedAt. Using confirmedAt as periodStart
        // for INDIVIDUAL as well would let the availability query silently
        // exclude the buyer's own already-scheduled session and produce a
        // false Insufficient.
        Instant periodStart = quote.getPurchaseType() == PurchaseType.INDIVIDUAL
            ? Instant.EPOCH
            : payload.confirmedAt();
        List<ScheduledSessionSnapshot> scheduledSessions = courseAvailabilityPort.findScheduledSessions(
            quote.getCourseId(), periodStart, payload.confirmedAt().plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );

        CoveragePlanner.Plan plan = switch (quote.getPurchaseType()) {
            case MONTHLY -> CoveragePlanner.planMonthly(
                scheduledSessions, payload.confirmedAt(), quote.getScheduledSessionCount(), false
            );
            case INDIVIDUAL -> CoveragePlanner.planIndividual(
                scheduledSessions, quote.getSelectedSessionId(), false
            );
        };

        if (!(plan instanceof CoveragePlanner.Plan.Complete complete)) {
            // Spec scenario: not enough sessions exist within the horizon —
            // no partial assignment is persisted, Purchase resolves to
            // EXCEPTION, Payment stays COMPLETED (design A4).
            log.warn(
                "Coverage shortfall resolving quote {} for outbox event paymentId={}; routing to EXCEPTION",
                quoteId, payload.paymentId()
            );
            publishPaymentFulfillmentFailed(paymentId, payment.getUserId(), Reason.TARGET_NOT_SCHEDULED);
            // #238: see the class javadoc on the payment==null branch above —
            // same direct-to-EXCEPTION construction, no markException call.
            purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, List.of());
            return;
        }

        List<CoveragePlanner.EligibleSession> eligibleSessions = complete.sessions();
        List<String> eligibleSessionIds = eligibleSessions.stream()
            .map(CoveragePlanner.EligibleSession::sessionId)
            .toList();
        purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload, eligibleSessionIds);

        List<SessionClaim> claims = eligibleSessions.stream()
            .map(session -> new SessionClaim(UUID.fromString(session.sessionId()), session.scheduledAt()))
            .toList();
        MultiSessionCapacityAssignmentCommand cmd = new MultiSessionCapacityAssignmentCommand(
            claims, payment.getUserId(), payload.paymentId()
        );

        try {
            capacityAdapter.assignAll(cmd);
            // design A3 Data Flow: "assignAll(ordered claims) -- ok --> purchase.assigned()".
            assignedAdapter.markAssigned(paymentId);
        } catch (com.menta.physical.domain.exception.CapacityBelowAssignedException capacityTripped) {
            // Spec scenario: capacity invariant trips — Purchase flips to EXCEPTION.
            // V7 UNIQUE race on (session_id, student_id) also rolls up here
            // — the adapter rethrows CapacityBelowAssignedException on a V7
            // UNIQUE collision. All-or-nothing (design A3): every insert in
            // this set is rolled back with it, so zero partial rows survive.
            try {
                exceptionAdapter.markException(paymentId, Reason.CAPACITY_BELOW_ASSIGNED);
            } catch (com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException alreadyAssigned) {
                // Idempotent redelivery (spec scenario 5): assignAll already
                // succeeded once and marked the Purchase ASSIGNED; this second
                // delivery re-attempts the same claims, trips the same V7
                // UNIQUE collision on the already-owned rows, and lands here.
                // ADR-0028 refuses ASSIGNED -> EXCEPTION on purpose (once
                // assigned, the residual path is unreachable) — that refusal
                // itself IS the correct, terminal outcome for a duplicate
                // delivery, not a failure to retry. Zero rows changed.
                log.info(
                    "Redelivered outbox event for an already-ASSIGNED purchase paymentId={}; no-op",
                    payload.paymentId()
                );
            }
        }
    }

    /**
     * Publishes {@code billing.PaymentFulfillmentFailed} (design C1/C2,
     * proposal D10) immediately before each of the three pre-{@code
     * Purchase} {@code markException} call sites, in its own {@code
     * REQUIRES_NEW} transaction so it survives when {@code markException}
     * throws {@code PaymentNotFoundException} and marks this method's
     * ambient (worker) transaction rollback-only — the still-unfixed C1
     * defect this change deliberately does not repair.
     *
     * <p>A {@link DataIntegrityViolationException} on redelivery (the
     * unique-index backstop, D5-style) is caught and logged as "already
     * notified" rather than rethrown: {@code markException} below is
     * always attempted afterward regardless of whether this publish
     * succeeded, was a duplicate, or is itself doomed to fail again.</p>
     */
    private void publishPaymentFulfillmentFailed(
        com.menta.billing.domain.model.PaymentId paymentId, UUID userId, Reason reason
    ) {
        try {
            publishPaymentFulfillmentFailedAdapter.publish(paymentId, userId, reason);
        } catch (DataIntegrityViolationException alreadyNotified) {
            log.info(
                "Redelivered billing.PaymentFulfillmentFailed for paymentId={}; already notified, no-op",
                paymentId.getValue()
            );
        }
    }

    private PaymentCompletedOutboxPayload parse(OutboxRowJpaEntity row) {
        try {
            return objectMapper.readValue(row.getPayload(), PaymentCompletedOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to deserialize billing.PhysicalPaymentCompleted payload", e
            );
        }
    }
}
