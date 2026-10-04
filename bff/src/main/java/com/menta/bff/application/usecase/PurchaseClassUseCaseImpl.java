package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link PurchaseClassUseCase}.
 *
 * <p>A rejected checkout ({@code 400}) means the form was tampered with or
 * the quote vanished; both are answered like an expired quote, since quoting
 * again is the only remedy the student has.
 * </p>
 */
@RequiredArgsConstructor
public class PurchaseClassUseCaseImpl implements PurchaseClassUseCase {

    private final PhysicalPurchaseApiClient physicalPurchaseApiClient;

    @Override
    public ClassPurchaseView execute(
            String courseId, String quoteId, ClassPaymentMethod paymentMethod, String idempotencyKey,
            String accessToken) {
        ClassCheckout checkout;
        try {
            checkout = physicalPurchaseApiClient.checkout(quoteId, paymentMethod, idempotencyKey, accessToken);
        } catch (PhysicalPurchaseApiClient.CapacityUnavailableException e) {
            return new ClassPurchaseView.SoldOut(courseId);
        } catch (PhysicalPurchaseApiClient.QuoteExpiredException
                 | PhysicalPurchaseApiClient.QuoteRejectedException e) {
            return new ClassPurchaseView.QuoteExpired(courseId);
        }

        if (paymentMethod == ClassPaymentMethod.MERCADO_PAGO && checkout.checkoutUrl() != null) {
            return new ClassPurchaseView.RedirectToProvider(checkout.checkoutUrl());
        }
        if (paymentMethod == ClassPaymentMethod.BANK_TRANSFER && checkout.bankTransfer() != null) {
            return new ClassPurchaseView.BankTransfer(courseId, checkout.bankTransfer());
        }
        throw new PhysicalPurchaseApiClient.ServiceUnavailableException(
                "Billing returned a checkout with no way to pay for " + paymentMethod);
    }
}
