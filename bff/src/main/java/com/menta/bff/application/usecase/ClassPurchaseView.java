package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassCheckout;

/**
 * Result of {@link PurchaseClassUseCase}: where the browser goes after the
 * payment form.
 */
public sealed interface ClassPurchaseView {

    /**
     * Mercado Pago: send the browser to the provider's checkout.
     *
     * @param checkoutUrl provider URL returned by billing
     */
    record RedirectToProvider(String checkoutUrl) implements ClassPurchaseView {
    }

    /**
     * Bank transfer: show where to transfer and the reference to quote.
     *
     * @param courseId     purchased course, for the back link
     * @param instructions transfer instructions
     */
    record BankTransfer(String courseId, ClassCheckout.BankTransfer instructions) implements ClassPurchaseView {
    }

    /**
     * The class filled up between quote and payment.
     *
     * @param courseId course, for the back link
     */
    record SoldOut(String courseId) implements ClassPurchaseView {
    }

    /**
     * The quote expired (or billing no longer knows it): quote again.
     *
     * @param courseId course, for the back link
     */
    record QuoteExpired(String courseId) implements ClassPurchaseView {
    }
}
