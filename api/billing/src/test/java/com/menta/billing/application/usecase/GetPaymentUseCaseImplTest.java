package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PaymentStatusResult;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reads a payment's current status (#31, US-BILLING-003, design C9). {@code updatedAt} is derived,
 * never a stored column — see {@link Payment#statusChangedAt()}'s Javadoc.
 */
class GetPaymentUseCaseImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-09-19T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private static Payment paymentWith(PaymentStatus status) {
        return new Payment(
            PAYMENT_ID, USER_ID, null, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual("plan-1"), status, CREATED_AT
        );
    }

    @Test
    void a_terminal_payment_reports_status_createdAt_and_updatedAt_from_the_status_change() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.Completed(CHANGED_AT))));
        GetPaymentUseCaseImpl useCase = new GetPaymentUseCaseImpl(paymentRepository);

        PaymentStatusResult result = useCase.getPayment(PAYMENT_ID.toString(), USER_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.updatedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    void a_non_terminal_payment_reports_updatedAt_equal_to_createdAt() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        GetPaymentUseCaseImpl useCase = new GetPaymentUseCaseImpl(paymentRepository);

        PaymentStatusResult result = useCase.getPayment(PAYMENT_ID.toString(), USER_ID);

        assertThat(result.status()).isEqualTo("AWAITING_MANUAL_VERIFICATION");
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.updatedAt()).isEqualTo(CREATED_AT);
    }

    /** C8: a non-owner and a missing payment look identical — never a 403. */
    @Test
    void a_non_owner_gets_PaymentNotFoundException_not_the_other_users_payment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification())));
        GetPaymentUseCaseImpl useCase = new GetPaymentUseCaseImpl(paymentRepository);

        assertThatThrownBy(() -> useCase.getPayment(PAYMENT_ID.toString(), UUID.randomUUID()))
            .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void a_missing_payment_throws_PaymentNotFoundException() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());
        GetPaymentUseCaseImpl useCase = new GetPaymentUseCaseImpl(paymentRepository);

        assertThatThrownBy(() -> useCase.getPayment(PAYMENT_ID.toString(), USER_ID))
            .isInstanceOf(PaymentNotFoundException.class);
    }
}
