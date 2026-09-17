package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import com.menta.billing.domain.model.Reason;
import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.in.PhysicalCapacityHoldPort;
import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.physical.application.usecase.CapacityAssignments;
import com.menta.physical.application.usecase.ConvertOutcome;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import com.menta.shared.physical.SessionClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: every assertion references the new
 * {@link PhysicalCapacityAssignmentOutboxEventHandler}, the api:app side
 * of the post-payment presential flow (proposal §4 handler;
 * design §6 reconciler integration).
 *
 * <p>#41 PR6 replaces the interim N=1 pass-through with real resolution:
 * {@code PaymentTarget.Physical}'s reference is loaded as a real {@link
 * PhysicalCourseQuote}, {@link CoveragePlanner} computes the eligible
 * session set, and {@link PhysicalCapacityAssignmentPort#assignAll} claims
 * every session in one ordered, all-or-nothing call.</p>
 */
class PhysicalCapacityAssignmentOutboxEventHandlerTest {

    private static final UUID PAYMENT_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID QUOTE_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String QUOTE_ID_STR = QUOTE_UUID.toString();
    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SESSION_UUID_2 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String SESSION_ID_STR = SESSION_UUID.toString();
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final PaymentId PAYMENT_ID = PaymentId.of(PAYMENT_UUID);
    private static final String COURSE_ID = "course-1";
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");

    private PhysicalCapacityAssignmentAdapter capacityAdapter;
    private PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;
    private PurchaseCreationFromEventPort purchaseCreationFromEventPort;
    private MarkPurchaseExceptionAdapter markExceptionAdapter;
    private MarkPurchaseAssignedAdapter markAssignedAdapter;
    private PublishPaymentFulfillmentFailedAdapter publishPaymentFulfillmentFailedAdapter;
    private PaymentRepository paymentRepository;
    private PhysicalCourseQuoteRepository quoteRepository;
    private PhysicalCourseAvailabilityPort courseAvailabilityPort;
    private PhysicalCapacityHoldPort physicalCapacityHoldPort;
    private PhysicalCapacityAssignmentOutboxEventHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        physicalCapacityAssignmentPort = mock(PhysicalCapacityAssignmentPort.class);
        capacityAdapter = new PhysicalCapacityAssignmentAdapter(physicalCapacityAssignmentPort);
        purchaseCreationFromEventPort = mock(PurchaseCreationFromEventPort.class);
        markExceptionAdapter = mock(MarkPurchaseExceptionAdapter.class);
        markAssignedAdapter = mock(MarkPurchaseAssignedAdapter.class);
        publishPaymentFulfillmentFailedAdapter = mock(PublishPaymentFulfillmentFailedAdapter.class);
        paymentRepository = mock(PaymentRepository.class);
        quoteRepository = mock(PhysicalCourseQuoteRepository.class);
        courseAvailabilityPort = mock(PhysicalCourseAvailabilityPort.class);
        physicalCapacityHoldPort = mock(PhysicalCapacityHoldPort.class);
        // Default: no hold exists for any payment (ConvertOutcome.HoldNotFound)
        // — every pre-existing test in this file exercises the legacy,
        // unheld path and must keep doing so unchanged.
        when(physicalCapacityHoldPort.convertAll(any(), any()))
            .thenReturn(ConvertOutcome.HoldNotFound.INSTANCE);
        objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        handler = new PhysicalCapacityAssignmentOutboxEventHandler(
            capacityAdapter, markExceptionAdapter, markAssignedAdapter, purchaseCreationFromEventPort,
            paymentRepository, quoteRepository, courseAvailabilityPort, physicalCapacityHoldPort,
            publishPaymentFulfillmentFailedAdapter, objectMapper
        );
    }

    private PaymentCompletedOutboxPayload payload() {
        return new PaymentCompletedOutboxPayload(
            PAYMENT_UUID,
            "mp-1", "ext-1", "merchant-1",
            QUOTE_ID_STR,
            new BigDecimal("1500.00"), "ARS",
            NOW
        );
    }

    private Payment physicalPayment() {
        return new Payment(
            PAYMENT_ID, STUDENT_UUID, "mp-1",
            Money.of(new BigDecimal("1500.00"), "ARS"),
            "ext-1", "merchant-1",
            new PaymentTarget.Physical(QUOTE_ID_STR),
            new PaymentStatus.Completed(NOW),
            NOW
        );
    }

    private PhysicalCourseQuote monthlyQuote(int scheduledSessionCount) {
        return PhysicalCourseQuote.reconstitute(
            QUOTE_UUID, COURSE_ID, PurchaseType.MONTHLY,
            Money.of(new BigDecimal("1500.00"), "ARS"), BigDecimal.ZERO, 1,
            scheduledSessionCount, null,
            Money.of(new BigDecimal("1500.00"), "ARS"), QuoteAvailability.AVAILABLE,
            NOW.minusSeconds(3600), NOW.plusSeconds(3600)
        );
    }

    private PhysicalCourseQuote individualQuote(String selectedSessionId) {
        return PhysicalCourseQuote.reconstitute(
            QUOTE_UUID, COURSE_ID, PurchaseType.INDIVIDUAL,
            Money.of(new BigDecimal("1500.00"), "ARS"), BigDecimal.ZERO, 1,
            1, selectedSessionId,
            Money.of(new BigDecimal("500.00"), "ARS"), QuoteAvailability.AVAILABLE,
            NOW.minusSeconds(3600), NOW.plusSeconds(3600)
        );
    }

    private OutboxRowJpaEntity rowWithPayload(PaymentCompletedOutboxPayload payload) throws Exception {
        OutboxRowJpaEntity row = mock(OutboxRowJpaEntity.class);
        when(row.getEventType()).thenReturn(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED);
        when(row.getPayload()).thenReturn(objectMapper.writeValueAsString(payload));
        return row;
    }

    @Test
    @DisplayName("Physical adapter exposes Physical's assignment boundary")
    void capacity_adapter_declares_a_physical_assignment_constructor() {
        assertThatCode(() -> PhysicalCapacityAssignmentAdapter.class.getDeclaredConstructor(
            PhysicalCapacityAssignmentPort.class
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Physical adapter delegates an assignment command unchanged")
    void capacity_adapter_delegates_assignment_unchanged() {
        CapacityAssignmentCommand command = new CapacityAssignmentCommand(
            SESSION_UUID, STUDENT_UUID, PAYMENT_UUID
        );
        when(physicalCapacityAssignmentPort.assign(command)).thenReturn(AssignmentOutcome.ASSIGNED.INSTANCE);

        AssignmentOutcome outcome = capacityAdapter.assign(command);

        assertThat(outcome).isEqualTo(AssignmentOutcome.ASSIGNED.INSTANCE);
        verify(physicalCapacityAssignmentPort).assign(command);
    }

    @Test
    @DisplayName("Physical adapter delegates an assignAll command unchanged")
    void capacity_adapter_delegates_assignAll_unchanged() {
        MultiSessionCapacityAssignmentCommand command = new MultiSessionCapacityAssignmentCommand(
            List.of(new SessionClaim(SESSION_UUID, NOW)), STUDENT_UUID, PAYMENT_UUID
        );
        CapacityAssignments result = new CapacityAssignments(List.of(SESSION_UUID));
        when(physicalCapacityAssignmentPort.assignAll(command)).thenReturn(result);

        CapacityAssignments outcome = capacityAdapter.assignAll(command);

        assertThat(outcome).isEqualTo(result);
        verify(physicalCapacityAssignmentPort).assignAll(command);
    }

    @Nested
    @DisplayName("Spec: EventType routing")
    class Routing {

        @Test
        void supports_PhysicalPaymentCompleted_event_type_only() {
            assertThat(handler.supports(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED)).isTrue();
            assertThat(handler.supports("auth.AccountActivationRequested")).isFalse();
            assertThat(handler.supports("auth.RefreshRotated")).isFalse();
            assertThat(handler.supports("auth.PasswordResetRequested")).isFalse();
        }
    }

    @Nested
    @DisplayName("Spec scenario: Individual coverage (N=1) creates a PENDING_FULFILLMENT purchase + capacity row")
    class IndividualCoverageHappy {

        @Test
        void upserts_purchase_then_assigns_the_selected_session() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(individualQuote(SESSION_ID_STR)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any()))
                .thenReturn(List.of(new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)));
            when(physicalCapacityAssignmentPort.assignAll(any()))
                .thenReturn(new CapacityAssignments(List.of(SESSION_UUID)));

            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.of(SESSION_ID_STR)));
            verify(physicalCapacityAssignmentPort, times(1)).assignAll(new MultiSessionCapacityAssignmentCommand(
                List.of(new SessionClaim(SESSION_UUID, NOW.plusSeconds(3600))), STUDENT_UUID, PAYMENT_UUID
            ));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(markAssignedAdapter, times(1)).markAssigned(PAYMENT_ID);
            verify(publishPaymentFulfillmentFailedAdapter, never()).publish(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Monthly coverage resolves N ordered sessions and calls assignAll")
    class MonthlyCoverageHappy {

        @Test
        void monthly_quote_resolves_ordered_claims_and_calls_assignAll() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(monthlyQuote(2)));
            when(courseAvailabilityPort.findScheduledSessions(
                eq(COURSE_ID), eq(NOW), eq(NOW.plus(CoveragePlanner.COVERAGE_LOOKAHEAD))
            )).thenReturn(List.of(
                new ScheduledSessionSnapshot(SESSION_UUID_2.toString(), NOW.plusSeconds(7200), 5),
                new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)
            ));
            when(physicalCapacityAssignmentPort.assignAll(any()))
                .thenReturn(new CapacityAssignments(List.of(SESSION_UUID, SESSION_UUID_2)));

            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(1)).createPurchaseFromPaymentEvent(
                any(), eq(List.of(SESSION_ID_STR, SESSION_UUID_2.toString()))
            );
            verify(physicalCapacityAssignmentPort, times(1)).assignAll(new MultiSessionCapacityAssignmentCommand(
                List.of(
                    new SessionClaim(SESSION_UUID, NOW.plusSeconds(3600)),
                    new SessionClaim(SESSION_UUID_2, NOW.plusSeconds(7200))
                ),
                STUDENT_UUID, PAYMENT_UUID
            ));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(markAssignedAdapter, times(1)).markAssigned(PAYMENT_ID);
            verify(publishPaymentFulfillmentFailedAdapter, never()).publish(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Not enough sessions inside the horizon — EXCEPTION, no partial assignment")
    class CoverageShortfall {

        @Test
        void insufficient_sessions_routes_to_exception_without_creating_purchase_or_assigning() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(monthlyQuote(4)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of(
                new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5),
                new ScheduledSessionSnapshot(SESSION_UUID_2.toString(), NOW.plusSeconds(7200), 5)
            ));

            handler.handle(rowWithPayload(payload()));

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), eq(STUDENT_UUID), eq(Reason.TARGET_NOT_SCHEDULED)
            );
            // #238: the Purchase row is now built directly at EXCEPTION (empty
            // session list is legal only for that status) — markException is
            // never called, since there is no PENDING_FULFILLMENT row to flip.
            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.<String>of()));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
            verify(markAssignedAdapter, never()).markAssigned(any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Capacity invariant trips — Purchase flips to EXCEPTION")
    class CapacityTrip {

        @Test
        void markException_called_when_assignAll_throws_CapacityBelowAssigned() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(individualQuote(SESSION_ID_STR)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any()))
                .thenReturn(List.of(new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)));
            doThrow(new CapacityBelowAssignedException())
                .when(physicalCapacityAssignmentPort).assignAll(any());

            handler.handle(rowWithPayload(payload()));

            verify(markExceptionAdapter, times(1)).markException(
                eq(PAYMENT_ID), eq(Reason.CAPACITY_BELOW_ASSIGNED)
            );
            verify(markAssignedAdapter, never()).markAssigned(any());
            verify(publishPaymentFulfillmentFailedAdapter, never()).publish(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Redelivery on an already-ASSIGNED purchase is a safe no-op")
    class RedeliveryAfterAssigned {

        @Test
        void swallows_the_refused_ASSIGNED_to_EXCEPTION_transition_without_propagating() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(individualQuote(SESSION_ID_STR)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any()))
                .thenReturn(List.of(new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)));
            doThrow(new CapacityBelowAssignedException())
                .when(physicalCapacityAssignmentPort).assignAll(any());
            doThrow(new IllegalPurchaseStateTransitionException(
                PAYMENT_ID, FulfillmentStatus.ASSIGNED, FulfillmentStatus.EXCEPTION
            )).when(markExceptionAdapter).markException(eq(PAYMENT_ID), eq(Reason.CAPACITY_BELOW_ASSIGNED));

            assertThatCode(() -> handler.handle(rowWithPayload(payload()))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Spec scenario: Re-delivery with same payment_id is idempotent")
    class IdempotentRedelivery {

        @Test
        void still_calls_create_then_assignAll_again_on_a_second_delivery() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(individualQuote(SESSION_ID_STR)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any()))
                .thenReturn(List.of(new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)));
            when(physicalCapacityAssignmentPort.assignAll(any()))
                .thenReturn(new CapacityAssignments(List.of(SESSION_UUID)));

            handler.handle(rowWithPayload(payload()));
            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(2)).createPurchaseFromPaymentEvent(any(), any());
            verify(physicalCapacityAssignmentPort, times(2)).assignAll(any());
            verify(markAssignedAdapter, times(2)).markAssigned(PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("Spec scenario: Hold-expired / monthly-coverage-changed residual flips to EXCEPTION")
    class TargetMissing {

        @Test
        void purchase_built_directly_at_exception_when_payment_row_cannot_be_loaded() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            handler.handle(rowWithPayload(payload()));

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), isNull(), eq(Reason.TARGET_NOT_SCHEDULED)
            );
            // #238: the Purchase row is built directly at EXCEPTION (an empty
            // session list is legal only for that status) — markException is
            // never called, since there is no PENDING_FULFILLMENT row to flip
            // and the row already reflects the terminal state.
            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.<String>of()));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
            verify(quoteRepository, never()).findById(any());
            verify(markAssignedAdapter, never()).markAssigned(any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: quote reference resolves to no quote — EXCEPTION / TARGET_NOT_SCHEDULED")
    class QuoteMissing {

        @Test
        void purchase_built_directly_at_exception_when_quote_cannot_be_resolved() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.empty());

            handler.handle(rowWithPayload(payload()));

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), eq(STUDENT_UUID), eq(Reason.TARGET_NOT_SCHEDULED)
            );
            // #238: Purchase row built directly at EXCEPTION; markException
            // is never called for this site.
            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.<String>of()));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
            verify(markAssignedAdapter, never()).markAssigned(any());
        }
    }

    @Nested
    @DisplayName("Spec scenario (design step 8): a held payment converts without CoveragePlanner recomputation")
    class HeldConversion {

        @Test
        void converted_hold_creates_purchase_and_marks_assigned_without_recomputation() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityHoldPort.convertAll(PAYMENT_UUID, STUDENT_UUID))
                .thenReturn(new ConvertOutcome.Converted(List.of(SESSION_UUID, SESSION_UUID_2)));

            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(1)).createPurchaseFromPaymentEvent(
                any(), eq(List.of(SESSION_UUID.toString(), SESSION_UUID_2.toString()))
            );
            verify(markAssignedAdapter, times(1)).markAssigned(PAYMENT_ID);
            verify(quoteRepository, never()).findById(any());
            verify(courseAvailabilityPort, never()).findScheduledSessions(any(), any(), any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
            verify(markExceptionAdapter, never()).markException(any(), any());
        }

        @Test
        void already_converted_hold_is_a_redelivery_noop() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityHoldPort.convertAll(PAYMENT_UUID, STUDENT_UUID))
                .thenReturn(ConvertOutcome.AlreadyConverted.INSTANCE);

            assertThatCode(() -> handler.handle(rowWithPayload(payload()))).doesNotThrowAnyException();

            verify(purchaseCreationFromEventPort, never()).createPurchaseFromPaymentEvent(any(), any());
            verify(markAssignedAdapter, never()).markAssigned(any());
            verify(quoteRepository, never()).findById(any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
            verify(markExceptionAdapter, never()).markException(any(), any());
        }

        @Test
        void capacity_trip_during_conversion_routes_to_exception_with_zero_assignment_rows() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityHoldPort.convertAll(PAYMENT_UUID, STUDENT_UUID))
                .thenThrow(new CapacityBelowAssignedException());
            when(physicalCapacityHoldPort.heldSessionIds(PAYMENT_UUID)).thenReturn(List.of(SESSION_UUID));

            handler.handle(rowWithPayload(payload()));

            // Unlike the legacy path, a held purchase never creates its
            // Purchase row before conversion is attempted — it must be
            // created here from the hold's own set so markException has a
            // row to flip to EXCEPTION (see the handler's javadoc on this
            // catch block).
            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.of(SESSION_UUID.toString())));
            verify(markExceptionAdapter, times(1)).markException(
                eq(PAYMENT_ID), eq(Reason.CAPACITY_BELOW_ASSIGNED)
            );
            verify(markAssignedAdapter, never()).markAssigned(any());
            verify(quoteRepository, never()).findById(any());
            verify(physicalCapacityAssignmentPort, never()).assignAll(any());
        }

        @Test
        void redelivery_after_conversion_capacity_trip_on_an_already_ASSIGNED_purchase_is_a_safe_noop()
            throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityHoldPort.convertAll(PAYMENT_UUID, STUDENT_UUID))
                .thenThrow(new CapacityBelowAssignedException());
            when(physicalCapacityHoldPort.heldSessionIds(PAYMENT_UUID)).thenReturn(List.of(SESSION_UUID));
            doThrow(new IllegalPurchaseStateTransitionException(
                PAYMENT_ID, FulfillmentStatus.ASSIGNED, FulfillmentStatus.EXCEPTION
            )).when(markExceptionAdapter).markException(eq(PAYMENT_ID), eq(Reason.CAPACITY_BELOW_ASSIGNED));

            assertThatCode(() -> handler.handle(rowWithPayload(payload()))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Spec scenario (design step 8): HoldNotFound falls to the unchanged legacy plan+assignAll path")
    class HoldNotFoundFallsToLegacy {

        @Test
        void no_hold_for_the_payment_still_resolves_coverage_and_calls_assignAll() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityHoldPort.convertAll(PAYMENT_UUID, STUDENT_UUID))
                .thenReturn(ConvertOutcome.HoldNotFound.INSTANCE);
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(individualQuote(SESSION_ID_STR)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any()))
                .thenReturn(List.of(new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)));
            when(physicalCapacityAssignmentPort.assignAll(any()))
                .thenReturn(new CapacityAssignments(List.of(SESSION_UUID)));

            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.of(SESSION_ID_STR)));
            verify(physicalCapacityAssignmentPort, times(1)).assignAll(new MultiSessionCapacityAssignmentCommand(
                List.of(new SessionClaim(SESSION_UUID, NOW.plusSeconds(3600))), STUDENT_UUID, PAYMENT_UUID
            ));
            verify(markExceptionAdapter, never()).markException(any(), any());
            verify(markAssignedAdapter, times(1)).markAssigned(PAYMENT_ID);
            verify(publishPaymentFulfillmentFailedAdapter, never()).publish(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Spec scenario (design C1/C2, D10): the payment-level fallback fires at the three "
        + "pre-Purchase sites and swallows a duplicate-on-redelivery")
    class PaymentFulfillmentFailedFallback {

        @Test
        @DisplayName("DataIntegrityViolationException from the publish call is caught and logged, "
            + "never rethrown, and the Purchase row is still built directly at EXCEPTION afterward")
        void duplicate_publish_on_redelivery_is_swallowed_and_purchase_creation_still_runs() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());
            doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                .when(publishPaymentFulfillmentFailedAdapter).publish(any(), any(), any());

            assertThatCode(() -> handler.handle(rowWithPayload(payload()))).doesNotThrowAnyException();

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), isNull(), eq(Reason.TARGET_NOT_SCHEDULED)
            );
            // #238: no markException call at this site — the row is built
            // directly at EXCEPTION by createPurchaseFromPaymentEvent.
            verify(purchaseCreationFromEventPort, times(1))
                .createPurchaseFromPaymentEvent(any(), eq(List.<String>of()));
            verify(markExceptionAdapter, never()).markException(any(), any());
        }

        @Test
        @DisplayName("publish fires at site 202 (quote not found) with the resolved userId")
        void publish_fires_when_quote_not_found() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.empty());

            handler.handle(rowWithPayload(payload()));

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), eq(STUDENT_UUID), eq(Reason.TARGET_NOT_SCHEDULED)
            );
        }

        @Test
        @DisplayName("publish fires at site 239 (coverage shortfall) with the resolved userId")
        void publish_fires_when_coverage_shortfall() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(quoteRepository.findById(QUOTE_ID_STR)).thenReturn(Optional.of(monthlyQuote(4)));
            when(courseAvailabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of(
                new ScheduledSessionSnapshot(SESSION_ID_STR, NOW.plusSeconds(3600), 5)
            ));

            handler.handle(rowWithPayload(payload()));

            verify(publishPaymentFulfillmentFailedAdapter, times(1)).publish(
                eq(PAYMENT_ID), eq(STUDENT_UUID), eq(Reason.TARGET_NOT_SCHEDULED)
            );
        }
    }
}
