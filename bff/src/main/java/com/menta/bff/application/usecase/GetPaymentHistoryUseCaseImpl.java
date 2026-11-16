package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.OwnPayment;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PaymentApiClient;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetPaymentHistoryUseCase}.
 *
 * <p>Fails closed without propagating: a request without a token makes no call at all, and an
 * unavailable Billing API only replaces the section with a notice. Log lines carry the exception
 * class only, so neither the token nor an upstream body can reach them.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class GetPaymentHistoryUseCaseImpl implements GetPaymentHistoryUseCase {

    /** How many payments the page lists; the API defaults to twenty and allows up to a hundred. */
    static final int HISTORY_LIMIT = 20;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentApiClient paymentApiClient;
    private final ZoneId displayZone;

    @Override
    public PaymentHistoryView execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return new PaymentHistoryView(List.of(), true);
        }
        try {
            return new PaymentHistoryView(
                    paymentApiClient.listOwnPayments(accessToken, HISTORY_LIMIT).stream().map(this::toRow).toList(),
                    false);
        } catch (BillingApiClient.ServiceUnavailableException e) {
            log.warn("Payment history unavailable, rendering the page without it: {}", e.getClass().getSimpleName());
            return new PaymentHistoryView(List.of(), true);
        }
    }

    private PaymentHistoryView.Row toRow(OwnPayment payment) {
        return new PaymentHistoryView.Row(
                DATE.format(payment.createdAt().atZone(displayZone)), HistoryLabels.concept(payment),
                amountLabel(payment.amount(), payment.currency()), HistoryLabels.method(payment.method()),
                HistoryLabels.paymentStatus(payment.status()), HistoryLabels.paymentTone(payment.status()));
    }

    /**
     * Formats the amount for es-AR. A currency code that is not ISO 4217 keeps the amount and the code as
     * they came.
     */
    private static String amountLabel(BigDecimal amount, String currency) {
        try {
            return ClassLabels.amount(amount, currency);
        } catch (IllegalArgumentException e) {
            return amount.toPlainString() + " " + currency;
        }
    }
}
