package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateBankTransferSubscriptionUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;

/**
 * Dispatches {@code POST /billing/subscriptions} by {@code paymentMethod} (design D3):
 * {@code MERCADO_PAGO} reaches {@link CreateSubscriptionCheckoutUseCaseImpl} byte-identically —
 * this class adds no precondition of its own before delegating — and {@code BANK_TRANSFER}
 * reaches {@link CreateBankTransferSubscriptionUseCaseImpl}. Each delegate keeps owning its own
 * preconditions and behavior; this class owns only the dispatch.
 *
 * <p>{@code CreateSubscriptionCheckoutUseCaseImpl}'s own non-MP {@code IllegalArgumentException}
 * guard stays in place as an internal invariant — unreachable through this router in practice,
 * but a defense-in-depth safety net if routing were ever bypassed.</p>
 */
public class RoutingCreateSubscriptionCheckoutUseCase implements CreateSubscriptionCheckoutUseCase {

    private final CreateSubscriptionCheckoutUseCase mercadoPagoUseCase;
    private final CreateBankTransferSubscriptionUseCase bankTransferUseCase;

    public RoutingCreateSubscriptionCheckoutUseCase(
        CreateSubscriptionCheckoutUseCase mercadoPagoUseCase,
        CreateBankTransferSubscriptionUseCase bankTransferUseCase
    ) {
        this.mercadoPagoUseCase = mercadoPagoUseCase;
        this.bankTransferUseCase = bankTransferUseCase;
    }

    @Override
    public SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command) {
        return switch (command.paymentMethod()) {
            case MERCADO_PAGO -> mercadoPagoUseCase.create(command);
            case BANK_TRANSFER -> bankTransferUseCase.create(command);
        };
    }
}
