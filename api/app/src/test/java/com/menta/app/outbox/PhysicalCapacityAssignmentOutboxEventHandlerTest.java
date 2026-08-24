package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.app.billing.MarkPurchaseExceptionAdapter;
import com.menta.app.billing.PhysicalCapacityAssignmentAdapter;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.in.PurchaseCreationFromEventPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Reason;
import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.math.BigDecimal;
import java.time.Instant;
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
 */
class PhysicalCapacityAssignmentOutboxEventHandlerTest {

    private static final UUID PAYMENT_UUID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String SESSION_ID_STR = SESSION_UUID.toString();
    private static final PaymentId PAYMENT_ID = PaymentId.of(PAYMENT_UUID);
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");

    private PhysicalCapacityAssignmentAdapter capacityAdapter;
    private PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;
    private PurchaseCreationFromEventPort purchaseCreationFromEventPort;
    private MarkPurchaseExceptionAdapter markExceptionAdapter;
    private PaymentRepository paymentRepository;
    private PhysicalCapacityAssignmentOutboxEventHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        physicalCapacityAssignmentPort = mock(PhysicalCapacityAssignmentPort.class);
        capacityAdapter = new PhysicalCapacityAssignmentAdapter(physicalCapacityAssignmentPort);
        purchaseCreationFromEventPort = mock(PurchaseCreationFromEventPort.class);
        markExceptionAdapter = mock(MarkPurchaseExceptionAdapter.class);
        paymentRepository = mock(PaymentRepository.class);
        objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        handler = new PhysicalCapacityAssignmentOutboxEventHandler(
            capacityAdapter, markExceptionAdapter, purchaseCreationFromEventPort, paymentRepository, objectMapper
        );
    }

    private PaymentCompletedOutboxPayload payload() {
        return new PaymentCompletedOutboxPayload(
            PAYMENT_UUID,
            "mp-1", "ext-1", "merchant-1",
            SESSION_ID_STR,
            new BigDecimal("1500.00"), "ARS",
            NOW
        );
    }

    private Payment physicalPayment() {
        return new Payment(
            PAYMENT_ID, STUDENT_UUID, "mp-1",
            Money.of(new BigDecimal("1500.00"), "ARS"),
            "ext-1", "merchant-1",
            new PaymentTarget.Physical(SESSION_ID_STR),
            new PaymentStatus.Completed(NOW),
            NOW
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
    @DisplayName("Spec scenario: First-time event creates a PENDING_FULFILLMENT purchase + capacity row")
    class FirstTimeHappy {

        @Test
        void upserts_purchase_then_assigns_capacity() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityAssignmentPort.assign(any())).thenReturn(AssignmentOutcome.ASSIGNED.INSTANCE);

            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(1)).createPurchaseFromPaymentEvent(any());
            verify(physicalCapacityAssignmentPort, times(1)).assign(
                org.mockito.ArgumentMatchers.eq(new CapacityAssignmentCommand(
                    SESSION_UUID, STUDENT_UUID, PAYMENT_UUID
                ))
            );
            verify(markExceptionAdapter, never()).markException(any(), any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Capacity invariant trips — Purchase flips to EXCEPTION")
    class CapacityTrip {

        @Test
        void markException_called_when_assign_throws_CapacityBelowAssigned() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            doThrow(new CapacityBelowAssignedException())
                .when(physicalCapacityAssignmentPort).assign(any());

            handler.handle(rowWithPayload(payload()));

            verify(markExceptionAdapter, times(1)).markException(
                org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                org.mockito.ArgumentMatchers.eq(Reason.CAPACITY_BELOW_ASSIGNED)
            );
        }
    }

    @Nested
    @DisplayName("Spec scenario: Re-delivery with same payment_id is idempotent")
    class IdempotentRedelivery {

        @Test
        void still_calls_create_then_assign_again_on_a_second_delivery() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(physicalPayment()));
            when(physicalCapacityAssignmentPort.assign(any())).thenReturn(AssignmentOutcome.ASSIGNED.INSTANCE);

            handler.handle(rowWithPayload(payload()));
            handler.handle(rowWithPayload(payload()));

            verify(purchaseCreationFromEventPort, times(2)).createPurchaseFromPaymentEvent(any());
            verify(physicalCapacityAssignmentPort, times(2)).assign(any());
        }
    }

    @Nested
    @DisplayName("Spec scenario: Hold-expired / monthly-coverage-changed residual flips to EXCEPTION")
    class TargetMissing {

        @Test
        void markException_called_when_payment_row_cannot_be_loaded() throws Exception {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            handler.handle(rowWithPayload(payload()));

            verify(markExceptionAdapter, times(1)).markException(
                org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                org.mockito.ArgumentMatchers.eq(Reason.TARGET_NOT_SCHEDULED)
            );
            verify(purchaseCreationFromEventPort, never()).createPurchaseFromPaymentEvent(any());
            verify(physicalCapacityAssignmentPort, never()).assign(any());
        }
    }
}
