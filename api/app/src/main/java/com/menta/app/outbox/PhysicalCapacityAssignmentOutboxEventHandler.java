package com.menta.app.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.app.billing.MarkPurchaseAssignedAdapter;
import com.menta.app.billing.MarkPurchaseExceptionAdapter;
import com.menta.app.billing.PhysicalCapacityAssignmentAdapter;
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
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ObjectMapper objectMapper;

    public PhysicalCapacityAssignmentOutboxEventHandler(
        PhysicalCapacityAssignmentAdapter capacityAdapter,
        MarkPurchaseExceptionAdapter exceptionAdapter,
        MarkPurchaseAssignedAdapter assignedAdapter,
        PurchaseCreationFromEventPort purchaseCreationFromEventPort,
        PaymentRepository paymentRepository,
        PhysicalCourseQuoteRepository quoteRepository,
        PhysicalCourseAvailabilityPort courseAvailabilityPort,
        ObjectMapper objectMapper
    ) {
        this.capacityAdapter = capacityAdapter;
        this.exceptionAdapter = exceptionAdapter;
        this.assignedAdapter = assignedAdapter;
        this.purchaseCreationFromEventPort = purchaseCreationFromEventPort;
        this.paymentRepository = paymentRepository;
        this.quoteRepository = quoteRepository;
        this.courseAvailabilityPort = courseAvailabilityPort;
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
            exceptionAdapter.markException(paymentId, Reason.TARGET_NOT_SCHEDULED);
            return;
        }

        String quoteId = physical.quoteId();
        PhysicalCourseQuote quote = quoteRepository.findById(quoteId).orElse(null);
        if (quote == null) {
            log.warn(
                "PhysicalCourseQuote {} not found for outbox event paymentId={}; routing to EXCEPTION",
                quoteId, payload.paymentId()
            );
            exceptionAdapter.markException(paymentId, Reason.TARGET_NOT_SCHEDULED);
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
            exceptionAdapter.markException(paymentId, Reason.TARGET_NOT_SCHEDULED);
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
