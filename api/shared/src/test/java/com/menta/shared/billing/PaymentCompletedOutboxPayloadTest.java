package com.menta.shared.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RED-GREEN: every assertion references {@link PaymentCompletedOutboxPayload},
 * which the production code path delivers as the single source of truth for
 * the {@code billing.PhysicalPaymentCompleted} event payload (proposal §3.1).
 *
 * <p>The compact constructor is the gatekeeper: it is the first thing {@link
 * com.menta.billing.application.usecase.PublishPhysicalPaymentCompletedUseCase}
 * invokes on the producer side and the first thing {@link
 * com.menta.app.outbox.PhysicalCapacityAssignmentOutboxEventHandler} sees on
 * the consumer side, so any relaxation here would silently desync the two
 * sides of the same outbox row.</p>
 */
class PaymentCompletedOutboxPayloadTest {

    private static final UUID VALID_PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_PROVIDER_PAYMENT_ID = "mp-1234567890";
    private static final String VALID_EXTERNAL_REFERENCE = "ext-menta-12345";
    private static final String VALID_MERCHANT_ACCOUNT_ID = "merchant-9876";
    private static final String VALID_TARGET_REFERENCE = "22222222-2222-2222-2222-222222222222";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("1500.00");
    private static final String VALID_CURRENCY = "ARS";
    private static final Instant VALID_CONFIRMED_AT = Instant.parse("2026-08-24T12:34:56Z");

    @Test
    void accepts_a_well_formed_payload_and_exposes_every_field_verbatim() {
        PaymentCompletedOutboxPayload payload = new PaymentCompletedOutboxPayload(
            VALID_PAYMENT_ID, VALID_PROVIDER_PAYMENT_ID, VALID_EXTERNAL_REFERENCE,
            VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE, VALID_AMOUNT, VALID_CURRENCY,
            VALID_CONFIRMED_AT
        );

        assertThat(payload.paymentId()).isEqualTo(VALID_PAYMENT_ID);
        assertThat(payload.providerPaymentId()).isEqualTo(VALID_PROVIDER_PAYMENT_ID);
        assertThat(payload.externalReference()).isEqualTo(VALID_EXTERNAL_REFERENCE);
        assertThat(payload.merchantAccountId()).isEqualTo(VALID_MERCHANT_ACCOUNT_ID);
        assertThat(payload.targetReference()).isEqualTo(VALID_TARGET_REFERENCE);
        assertThat(payload.amount()).isEqualByComparingTo(VALID_AMOUNT);
        assertThat(payload.currency()).isEqualTo(VALID_CURRENCY);
        assertThat(payload.confirmedAt()).isEqualTo(VALID_CONFIRMED_AT);
    }

    @Test
    void accepts_a_zero_amount_matchining_vo_profile_chk_billing_payments_amount_non_negative() {
        PaymentCompletedOutboxPayload payload = new PaymentCompletedOutboxPayload(
            VALID_PAYMENT_ID, VALID_PROVIDER_PAYMENT_ID, VALID_EXTERNAL_REFERENCE,
            VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE, BigDecimal.ZERO, VALID_CURRENCY,
            VALID_CONFIRMED_AT
        );

        assertThat(payload.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Nested
    @DisplayName("Compact constructor rejects invalid input")
    class InvalidInputRejected {

        @ParameterizedTest
        @MethodSource("blankOrTooLongStrings")
        void rejects_blank_or_oversize_strings(String invalidProviderPaymentId) {
            assertThatThrownBy(() -> new PaymentCompletedOutboxPayload(
                VALID_PAYMENT_ID, invalidProviderPaymentId, VALID_EXTERNAL_REFERENCE,
                VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE, VALID_AMOUNT, VALID_CURRENCY,
                VALID_CONFIRMED_AT
            )).isInstanceOf(IllegalArgumentException.class);
        }

        static Stream<String> blankOrTooLongStrings() {
            String oversized = "x".repeat(65);
            return Stream.of("", "   ", oversized);
        }

        @Test
        void rejects_negative_amount() {
            assertThatThrownBy(() -> new PaymentCompletedOutboxPayload(
                VALID_PAYMENT_ID, VALID_PROVIDER_PAYMENT_ID, VALID_EXTERNAL_REFERENCE,
                VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE,
                new BigDecimal("-0.01"), VALID_CURRENCY, VALID_CONFIRMED_AT
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "AR", "ARSX", "ARS "})
        void rejects_currency_other_than_exactly_three_iso4217_chars(String invalidCurrency) {
            assertThatThrownBy(() -> new PaymentCompletedOutboxPayload(
                VALID_PAYMENT_ID, VALID_PROVIDER_PAYMENT_ID, VALID_EXTERNAL_REFERENCE,
                VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE, VALID_AMOUNT, invalidCurrency,
                VALID_CONFIRMED_AT
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_null_payment_id_uuid() {
            assertThatThrownBy(() -> new PaymentCompletedOutboxPayload(
                null, VALID_PROVIDER_PAYMENT_ID, VALID_EXTERNAL_REFERENCE,
                VALID_MERCHANT_ACCOUNT_ID, VALID_TARGET_REFERENCE, VALID_AMOUNT, VALID_CURRENCY,
                VALID_CONFIRMED_AT
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
