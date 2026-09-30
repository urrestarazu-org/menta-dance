package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult;

/**
 * Entry port for a bank-transfer physical-course purchase checkout (#36, US-BILLING-008, design
 * C2).
 *
 * <p>Shares {@link CreatePhysicalPurchaseCheckoutCommand} with {@link
 * CreatePhysicalPurchaseCheckoutUseCase} so {@code RoutingCreatePhysicalPurchaseCheckoutUseCase}
 * (Phase 4) can dispatch to either implementation without reshaping the request. The result type
 * is a temporary, phase-scoped DTO — see {@link PhysicalPurchaseBankTransferCheckoutResult}'s
 * javadoc for why it is not yet {@code PhysicalPurchaseCheckoutResult}.</p>
 */
public interface CreateBankTransferPhysicalPurchaseUseCase {

    PhysicalPurchaseBankTransferCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command);
}
