package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreateBankTransferPhysicalPurchaseUseCase;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;

/**
 * Dispatches {@code POST /billing/physical/purchases} by {@code paymentMethod} (#36,
 * US-BILLING-008, design C3): {@code MERCADO_PAGO} reaches {@link
 * CreatePhysicalPurchaseCheckoutUseCaseImpl} byte-identically — this class adds no precondition of
 * its own before delegating — and {@code BANK_TRANSFER} reaches {@link
 * CreateBankTransferPhysicalPurchaseUseCaseImpl}. Each delegate keeps owning its own preconditions
 * and behavior; this class owns only the dispatch. Shape-identical to {@link
 * RoutingCreateSubscriptionCheckoutUseCase}.
 *
 * <p>Unlike the subscription router, the two delegates here do not share a single return type:
 * {@link CreateBankTransferPhysicalPurchaseUseCase#create} still returns {@code
 * PhysicalPurchaseBankTransferCheckoutResult} (Phase 3's phase-scoped bridge DTO). Design C5 —
 * {@code PhysicalPurchaseCheckoutResult.fromBankTransfer} — was pulled forward into this phase
 * specifically so this router can present one uniform {@code
 * CreatePhysicalPurchaseCheckoutUseCase}-shaped return type to callers: the {@code BANK_TRANSFER}
 * arm converts the bridge result into {@code PhysicalPurchaseCheckoutResult} right here, at the
 * router's own boundary, rather than changing {@link CreateBankTransferPhysicalPurchaseUseCaseImpl}
 * or retiring the bridge DTO.</p>
 */
public class RoutingCreatePhysicalPurchaseCheckoutUseCase implements CreatePhysicalPurchaseCheckoutUseCase {

    private final CreatePhysicalPurchaseCheckoutUseCase mercadoPagoUseCase;
    private final CreateBankTransferPhysicalPurchaseUseCase bankTransferUseCase;

    public RoutingCreatePhysicalPurchaseCheckoutUseCase(
        CreatePhysicalPurchaseCheckoutUseCase mercadoPagoUseCase,
        CreateBankTransferPhysicalPurchaseUseCase bankTransferUseCase
    ) {
        this.mercadoPagoUseCase = mercadoPagoUseCase;
        this.bankTransferUseCase = bankTransferUseCase;
    }

    @Override
    public PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command) {
        return switch (command.paymentMethod()) {
            case MERCADO_PAGO -> mercadoPagoUseCase.create(command);
            case BANK_TRANSFER -> toCheckoutResult(bankTransferUseCase.create(command));
        };
    }

    private static PhysicalPurchaseCheckoutResult toCheckoutResult(PhysicalPurchaseBankTransferCheckoutResult bridge) {
        return new PhysicalPurchaseCheckoutResult(
            bridge.paymentId(), bridge.quoteId(), bridge.status(), null, null, bridge.externalReference(),
            bridge.bankTransferInstructions()
        );
    }
}
