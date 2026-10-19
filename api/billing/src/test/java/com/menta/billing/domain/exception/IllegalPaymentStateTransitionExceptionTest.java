package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * #33, US-BILLING-005, design C1: the 4-arg constructor lets {@link
 * com.menta.billing.domain.model.Payment#correctManually} name a different expected starting
 * state ({@code ReconciliationRequired}) than the 3-arg constructor {@link
 * com.menta.billing.domain.model.Payment#resolveManually} already uses ({@code
 * AwaitingManualVerification}). The 3-arg constructor delegates to the 4-arg one and its
 * behavior — message, error code, getters — stays byte-unchanged.
 */
class IllegalPaymentStateTransitionExceptionTest {

    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final PaymentStatus STATUS = new PaymentStatus.Expired(Instant.parse("2026-09-20T10:00:00Z"));
    private static final ManualVerificationDecision DECISION = ManualVerificationDecision.APPROVED;

    @Test
    void the_4_arg_constructor_names_the_caller_supplied_expected_state() {
        IllegalPaymentStateTransitionException exception =
            new IllegalPaymentStateTransitionException(PAYMENT_ID, STATUS, DECISION, "ReconciliationRequired");

        assertThat(exception.getMessage())
            .isEqualTo(
                "Cannot apply decision " + DECISION + " to payment " + PAYMENT_ID
                    + ": current status is Expired, expected ReconciliationRequired"
            );
        assertThat(exception.getErrorCode()).isEqualTo("ILLEGAL_PAYMENT_STATE_TRANSITION");
        assertThat(exception.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(exception.getStatus()).isEqualTo(STATUS);
        assertThat(exception.getDecision()).isEqualTo(DECISION);
    }

    @Test
    void the_3_arg_constructor_still_delegates_to_awaiting_manual_verification() {
        IllegalPaymentStateTransitionException viaThreeArg =
            new IllegalPaymentStateTransitionException(PAYMENT_ID, STATUS, DECISION);
        IllegalPaymentStateTransitionException viaFourArg = new IllegalPaymentStateTransitionException(
            PAYMENT_ID, STATUS, DECISION, "AwaitingManualVerification"
        );

        assertThat(viaThreeArg.getMessage()).isEqualTo(viaFourArg.getMessage());
        assertThat(viaThreeArg.getErrorCode()).isEqualTo(viaFourArg.getErrorCode());
    }
}
